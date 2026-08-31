"""老吴外卖 · 智能客服 Agent 服务入口
启动：python -m app.main 或 uvicorn app.main:app --reload --port 8000
"""
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse

from app.api.chat import router as chat_router
from app.api.admin_sessions import router as admin_sessions_router
from app.api.admin_faq import router as admin_faq_router
from app.api.admin_reports import router as admin_reports_router
from app.config import get_settings
from app.graphs import customer_service as cs_graph
from app.tools.sky_client import close_sky_http_client, init_sky_http_client
from app.sessions import init_session_store


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时初始化会话持久化（SQLite checkpointer）
    settings.validate_runtime()
    await init_sky_http_client()
    init_session_store()
    try:
        await cs_graph.init_checkpointer()
        yield
    finally:
        await close_sky_http_client()


app = FastAPI(title="老吴外卖智能客服 Agent", version="0.1", lifespan=lifespan)
settings = get_settings()

# 开发期放开跨域（小程序/测试页调用）；生产收敛为白名单
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.parsed_cors_origins(),
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router, prefix="/agent")
app.include_router(admin_sessions_router)
app.include_router(admin_faq_router)
app.include_router(admin_reports_router)


@app.get("/health")
def health():
    return {"status": "ok", "service": "sky-agent"}


@app.get("/ready")
async def ready():
    checks = {"model_configured": bool(settings.deepseek_api_key), "java": False, "checkpoint": cs_graph.checkpointer is not None}
    try:
        from app.tools.sky_client import _shared_http_client
        if _shared_http_client is not None:
            response = await _shared_http_client.get(f"{settings.sky_server_base_url}/health")
            checks["java"] = response.status_code == 200
    except Exception:
        checks["java"] = False
    status = "ready" if all(checks.values()) else "not_ready"
    if status != "ready":
        raise HTTPException(status_code=503, detail={"status": status, "service": "sky-agent", "checks": checks})
    return {"status": status, "service": "sky-agent", "checks": checks}


@app.get("/", response_class=HTMLResponse)
def chat_page():
    """客服对话测试页（浏览器打开即用）"""
    html = Path(__file__).resolve().parent / "static" / "chat.html"
    return HTMLResponse(html.read_text(encoding="utf-8"))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=get_settings().agent_port, reload=True)
