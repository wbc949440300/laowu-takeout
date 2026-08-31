"""对话接口：
- POST /agent/chat          JSON 问答（含危险操作确认的 resume 续跑）
- POST /agent/chat/stream   SSE 流式输出
"""
import json
import time
import uuid

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import StreamingResponse
from langchain_core.messages import HumanMessage
from langgraph.types import Command
from pydantic import BaseModel, Field

from app.auth import CurrentUser, get_current_user
from app.graphs.customer_service import build_customer_service_graph
from app.tools.dish_tools import build_dish_tools
from app.tools.faq_tools import build_faq_tools
from app.tools.order_tools import build_order_tools
from app.tools.sky_client import SkyClient
from app.config import get_settings
from app.sessions import create_session, delete_session, get_session, list_sessions, update_session_feedback

router = APIRouter()
_CHAT_RATE_LIMIT = 30
_CHAT_RATE_WINDOW = 60.0
_chat_rate_state: dict[int, tuple[int, float]] = {}


class InterruptPendingError(Exception):
    """会话停在确认中断上时又收到新消息：需先完成或取消确认"""

    def __init__(self, payload: dict):
        super().__init__("confirm pending")
        self.payload = payload


class ChatRequest(BaseModel):
    message: str = Field(default="", max_length=10000)
    # 会话标识，首次可不传（自动生成），多轮需回传
    thread_id: str | None = None
    # 危险操作确认续跑：true=确认执行，false=放弃；普通对话不传
    resume: bool | None = None


def _build_graph(user: CurrentUser, trace_id: str | None = None):
    client = SkyClient(user.token, trace_id=trace_id)
    tools = build_order_tools(client) + build_dish_tools(client) + build_faq_tools()
    return build_customer_service_graph(tools)


def _check_chat_rate_limit(user_id: int) -> None:
    now = time.monotonic()
    count, started = _chat_rate_state.get(user_id, (0, now))
    if now - started >= _CHAT_RATE_WINDOW:
        count, started = 0, now
    count += 1
    _chat_rate_state[user_id] = (count, started)
    if count > _CHAT_RATE_LIMIT:
        raise HTTPException(status_code=429, detail="客服请求过于频繁，请稍后再试")
    if len(_chat_rate_state) > 10000:
        expired = [key for key, (_, timestamp) in _chat_rate_state.items()
                   if now - timestamp >= _CHAT_RATE_WINDOW]
        for key in expired[:5000]:
            _chat_rate_state.pop(key, None)


def _validate_chat_request(req: ChatRequest) -> None:
    if len(req.message) > get_settings().max_input_chars:
        raise HTTPException(status_code=413, detail="输入内容过长，请分段发送")
    if req.resume is None and not req.message:
        raise HTTPException(status_code=400, detail="message 不能为空")
    if req.resume is not None and not req.thread_id:
        raise HTTPException(status_code=400, detail="恢复操作必须提供 thread_id")


def _resolve_thread_id(user_id: int, supplied_thread_id: str | None) -> str:
    """Create a user-scoped checkpoint key and reject cross-user sessions."""
    if supplied_thread_id is None:
        return f"user:{user_id}:{uuid.uuid4().hex}"

    thread_id = supplied_thread_id.strip()
    expected_prefix = f"user:{user_id}:"
    session_part = thread_id[len(expected_prefix):] if thread_id.startswith(expected_prefix) else ""
    if not session_part or len(session_part) > 64 or not session_part.replace("-", "").isalnum():
        raise HTTPException(status_code=403, detail="会话不存在或不属于当前用户")
    return thread_id


def _session_for_request(user_id: int, supplied_thread_id: str | None, has_message: bool) -> dict:
    if supplied_thread_id is None:
        if not has_message:
            raise HTTPException(status_code=400, detail="message 不能为空")
        return create_session(user_id)
    thread_id = _resolve_thread_id(user_id, supplied_thread_id)
    session = get_session(user_id, thread_id)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在、已过期或已删除")
    return session


def _extract_interrupt(state):
    """检查图是否停在确认中断上"""
    for task in state.tasks:
        if task.interrupts:
            return task.interrupts[0].value
    return None


def _interrupt_expired(interrupt_payload) -> bool:
    expires_at = interrupt_payload.get("expires_at") if isinstance(interrupt_payload, dict) else None
    return not isinstance(expires_at, (int, float)) or expires_at < time.time()


def _validate_resume_interrupt(interrupt_payload) -> None:
    if not interrupt_payload:
        raise HTTPException(status_code=409, detail="当前会话没有待确认操作")
    if _interrupt_expired(interrupt_payload):
        raise HTTPException(status_code=409, detail="确认操作已过期，请重新发起")


async def _clear_expired_interrupt(graph, config, interrupt_payload) -> None:
    """确认已过期：自动按放弃处理，解除会话中断态，避免会话永久卡死"""
    if interrupt_payload and _interrupt_expired(interrupt_payload):
        await graph.ainvoke(Command(resume=False), config)


def _final_answer(state) -> str:
    """从会话消息中取最后一条 AI 回复"""
    for m in reversed(state.values.get("messages", [])):
        if getattr(m, "type", "") == "ai" and getattr(m, "content", ""):
            return m.content
    return ""


async def _run(req: ChatRequest, user: CurrentUser, trace_id: str | None = None):
    session = _session_for_request(user.user_id, req.thread_id, bool(req.message))
    graph = _build_graph(user, trace_id=trace_id)
    thread_id = session["thread_id"]
    config = {"configurable": {"thread_id": thread_id}}

    if req.resume is not None:
        pending_state = await graph.aget_state(config)
        _validate_resume_interrupt(_extract_interrupt(pending_state))
        await graph.ainvoke(Command(resume=req.resume), config)
    elif req.message:
        # 新消息前先检查中断态：停在确认框的会话必须先 resume（确认/放弃），
        # 否则直接 ainvoke 会抛异常；过期的确认自动按放弃清理后继续处理新消息
        pending_state = await graph.aget_state(config)
        pending_interrupt = _extract_interrupt(pending_state)
        if pending_interrupt:
            if _interrupt_expired(pending_interrupt):
                await _clear_expired_interrupt(graph, config, pending_interrupt)
            else:
                raise InterruptPendingError(pending_interrupt)
        await graph.ainvoke({"messages": [HumanMessage(content=req.message)]}, config)

    state = await graph.aget_state(config)
    interrupt_payload = _extract_interrupt(state)
    return graph, config, thread_id, state, interrupt_payload


@router.post("/chat")
async def chat(req: ChatRequest, user: CurrentUser = Depends(get_current_user), x_trace_id: str | None = Header(default=None)):
    _check_chat_rate_limit(user.user_id)
    if len(req.message) > get_settings().max_input_chars:
        raise HTTPException(status_code=413, detail="输入内容过长，请分段发送")
    if req.resume is None and not req.message:
        return {"type": "error", "msg": "message 不能为空"}
    if req.resume is not None and not req.thread_id:
        raise HTTPException(status_code=400, detail="恢复操作必须提供 thread_id")

    trace_id = x_trace_id or uuid.uuid4().hex
    try:
        _, _, thread_id, state, interrupt_payload = await _run(req, user, trace_id)
    except HTTPException:
        raise
    except InterruptPendingError as exc:
        # 明确的错误码：前端应引导用户先完成或取消确认（带 resume 重新调用）
        return {
            "type": "error",
            "thread_id": req.thread_id,
            "error": {
                "code": "CONFIRM_PENDING",
                "message": "当前会话有待确认操作，请先完成或取消确认（携带 resume 参数重新调用）后再发送新消息",
                "retryable": False,
            },
            "interrupt": exc.payload,
            "trace_id": trace_id,
        }
    except Exception:
        return {"type": "error", "thread_id": req.thread_id, "error": {"code": "MODEL_UNAVAILABLE", "message": "客服暂时不可用，请稍后重试", "retryable": True}, "trace_id": trace_id}

    # 停在确认节点：前端渲染确认框，用户点击后带 resume 再次调用
    if interrupt_payload:
        return {"type": "confirm", "thread_id": thread_id, "interrupt": interrupt_payload, "trace_id": trace_id}

    return {"type": "reply", "thread_id": thread_id, "answer": _final_answer(state), "usage": state.values.get("usage", {}), "trace_id": trace_id}


@router.post("/chat/stream")
async def chat_stream(req: ChatRequest, user: CurrentUser = Depends(get_current_user)):
    """SSE 流式：逐段输出 AI 回复；结束时若是确认中断则下发 confirm 事件"""
    _check_chat_rate_limit(user.user_id)
    _validate_chat_request(req)
    trace_id = uuid.uuid4().hex
    if len(req.message) > get_settings().max_input_chars:
        raise HTTPException(status_code=413, detail="输入内容过长，请分段发送")
    if req.resume is not None and not req.thread_id:
        raise HTTPException(status_code=400, detail="恢复操作必须提供 thread_id")
    session = _session_for_request(user.user_id, req.thread_id, bool(req.message) or req.resume is not None)
    thread_id = session["thread_id"]
    graph = _build_graph(user, trace_id=trace_id)
    config = {"configurable": {"thread_id": thread_id}}

    if req.resume is not None:
        pending_state = await graph.aget_state(config)
        _validate_resume_interrupt(_extract_interrupt(pending_state))
        inputs = Command(resume=req.resume)
    else:
        # 与 /chat 一致：停在中断态的会话不能直接收新消息（过期确认自动清理）
        pending_state = await graph.aget_state(config)
        pending_interrupt = _extract_interrupt(pending_state)
        if pending_interrupt:
            if _interrupt_expired(pending_interrupt):
                await _clear_expired_interrupt(graph, config, pending_interrupt)
            else:
                raise HTTPException(status_code=409, detail="当前会话有待确认操作，请先完成或取消确认后再发送新消息")
        inputs = {"messages": [HumanMessage(content=req.message)]}

    async def event_gen():
        try:
            async for msg, meta in graph.astream(inputs, config, stream_mode="messages"):
                if getattr(msg, "type", "") == "AIMessageChunk" and msg.content:
                    yield f"data: {json.dumps({'delta': msg.content}, ensure_ascii=False)}\n\n"
        except Exception:
            yield f"data: {json.dumps({'error': {'code': 'MODEL_UNAVAILABLE', 'message': '客服暂时不可用，请稍后重试', 'retryable': True}, 'trace_id': trace_id}, ensure_ascii=False)}\n\n"
            return

        state = await graph.aget_state(config)
        interrupt_payload = _extract_interrupt(state)
        if interrupt_payload:
            yield f"data: {json.dumps({'confirm': interrupt_payload, 'thread_id': thread_id, 'trace_id': trace_id}, ensure_ascii=False)}\n\n"
        yield f"data: {json.dumps({'done': True, 'thread_id': thread_id, 'trace_id': trace_id}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_gen(), media_type="text/event-stream")


@router.get("/sessions")
async def sessions(user: CurrentUser = Depends(get_current_user)):
    return {"sessions": list_sessions(user.user_id)}


@router.delete("/sessions/{thread_id}")
async def remove_session(thread_id: str, user: CurrentUser = Depends(get_current_user)):
    thread_id = _resolve_thread_id(user.user_id, thread_id)
    if not delete_session(user.user_id, thread_id):
        raise HTTPException(status_code=404, detail="会话不存在或已删除")
    return {"deleted": True, "thread_id": thread_id}

class SessionFeedback(BaseModel):
    rating: int = Field(ge=1, le=5)
    feedback: str | None = Field(default=None, max_length=2000)

@router.patch("/sessions/{thread_id}/feedback")
async def session_feedback(thread_id: str, payload: SessionFeedback, user: CurrentUser = Depends(get_current_user)):
    thread_id = _resolve_thread_id(user.user_id, thread_id)
    session = update_session_feedback(user.user_id, thread_id, payload.rating, payload.feedback)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在或已删除")
    return {"thread_id": thread_id, "rating": session["rating"], "feedback": session["feedback"]}
