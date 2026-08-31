"""LangGraph 智能客服图：
START → agent(LLM+工具) → 无工具调用=直接回复 / 只读工具=执行后回 agent / 危险工具=confirm 中断等确认
"""
import os
import time
from pathlib import Path

import aiosqlite
from langchain_core.messages import SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import ToolNode
from langgraph.types import Command, interrupt

from app.config import get_settings
from app.prompts.customer_service import SYSTEM_PROMPT
from app.state import AgentState

# 需要明确确认的工具，以及所有会改变业务状态的工具。
CONFIRM_REQUIRED_TOOLS = {"cancel_order", "apply_refund", "repetition_order"}
SIDE_EFFECT_TOOLS = CONFIRM_REQUIRED_TOOLS | {"remind_order", "repetition_order"}
# 保留原名称，兼容已有测试和引用。
DANGEROUS_TOOLS = CONFIRM_REQUIRED_TOOLS


def route_tool_calls(calls: list[dict]):
    """Classify calls before execution so one confirmation never approves a batch."""
    if not calls:
        return END
    if len(calls) > 1 and any(call["name"] in SIDE_EFFECT_TOOLS for call in calls):
        return "split"
    if calls[0]["name"] in CONFIRM_REQUIRED_TOOLS:
        return "confirm"
    return "tools"

# 会话记忆：SQLite 异步持久化（服务重启会话不丢；路径可由环境变量 CHECKPOINT_DB 覆盖）
# 实际连接在 FastAPI 启动时由 app.main.init_checkpointer() 初始化
_DB_PATH = os.environ.get("CHECKPOINT_DB") or str(
    Path(__file__).resolve().parent.parent.parent / "checkpoints.db"
)
checkpointer: AsyncSqliteSaver | None = None


async def init_checkpointer():
    """应用启动时调用：建立 SQLite 连接并创建表"""
    global checkpointer
    conn = await aiosqlite.connect(_DB_PATH)
    saver = AsyncSqliteSaver(conn)
    await saver.setup()
    checkpointer = saver


def _build_llm(tools):
    settings = get_settings()
    if not settings.deepseek_api_key:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY：请复制 .env.example 为 .env 并填入 API Key")
    llm = ChatOpenAI(
        model=settings.deepseek_model,
        api_key=settings.deepseek_api_key,
        base_url=settings.deepseek_base_url,
        temperature=0.3,
        max_tokens=settings.max_output_tokens,
    )
    return llm.bind_tools(tools)


def build_customer_service_graph(tools):
    """按请求构建客服图（工具闭包绑定当前用户 token，确保身份隔离）"""
    llm_with_tools = _build_llm(tools)

    async def agent_node(state: AgentState):
        settings = get_settings()
        history = state.get("messages", [])[-settings.max_history_messages:]
        total = 0
        trimmed = []
        for message in reversed(history):
            content = getattr(message, "content", "") or ""
            size = len(content) if isinstance(content, str) else len(str(content))
            if total + size > settings.max_input_chars and trimmed:
                break
            trimmed.append(message)
            total += size
        messages = [SystemMessage(content=SYSTEM_PROMPT), *reversed(trimmed)]
        response = await llm_with_tools.ainvoke(messages)
        metadata = getattr(response, "response_metadata", {}) or {}
        usage = metadata.get("token_usage") or metadata.get("usage") or {}
        return {"messages": [response], "usage": usage}

    def route_after_agent(state: AgentState):
        """按工具调用分流：无调用=结束；危险工具=确认节点；其余=直接执行"""
        last = state["messages"][-1]
        calls = getattr(last, "tool_calls", None) or []
        return route_tool_calls(calls)

    async def split_calls_node(state: AgentState):
        """Reject mixed/batched mutations and ask the model to retry one action at a time."""
        calls = state["messages"][-1].tool_calls
        return {
            "messages": [
                ToolMessage(
                    content="本轮包含多项操作且至少一项会修改业务状态，未执行。请一次只发起一个操作。",
                    tool_call_id=call["id"],
                )
                for call in calls
            ]
        }

    async def confirm_node(state: AgentState):
        """危险操作确认：interrupt 挂起等待前端用户点确认/取消"""
        last = state["messages"][-1]
        call = last.tool_calls[0]
        approved = interrupt({
            "action": call["name"],
            "args": call["args"],
            "tool_call_id": call["id"],
            "expires_at": int(time.time()) + 300,
        })
        if approved:
            # 用户确认 → 进入工具节点真实执行
            return Command(goto="tools")
        # 用户拒绝 → 回填工具消息，让 agent 委婉结束
        return Command(
            goto="agent",
            update={
                "messages": [
                    ToolMessage(content="用户已取消该操作，未执行。", tool_call_id=call["id"])
                ]
            },
        )

    graph = StateGraph(AgentState)
    graph.add_node("agent", agent_node)
    graph.add_node("tools", ToolNode(tools))
    graph.add_node("confirm", confirm_node)
    graph.add_node("split", split_calls_node)

    graph.add_edge(START, "agent")
    graph.add_conditional_edges(
        "agent", route_after_agent, {"tools": "tools", "confirm": "confirm", "split": "split", END: END}
    )
    graph.add_edge("tools", "agent")
    graph.add_edge("split", "agent")

    if checkpointer is None:
        raise RuntimeError("checkpointer 未初始化：应用需经 app.main 启动（lifespan 会初始化）")
    return graph.compile(checkpointer=checkpointer)
