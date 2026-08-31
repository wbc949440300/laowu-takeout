from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, Field

from app.auth import CurrentAdmin, get_current_admin
from app.sessions import admin_get_session, admin_list_sessions, admin_session_summary, admin_update_session, add_audit

router = APIRouter(prefix="/admin/sessions", tags=["admin-sessions"])

class SessionUpdate(BaseModel):
    conversation_status: str | None = None
    owner_id: int | None = None
    handoff_reason: str | None = Field(default=None, max_length=500)
    rating: int | None = Field(default=None, ge=1, le=5)
    feedback: str | None = Field(default=None, max_length=2000)

@router.get("")
def list_admin_sessions(page: int = 1, page_size: int = 20, status: str | None = None, user_id: int | None = None, admin: CurrentAdmin = Depends(get_current_admin)):
    records, total = admin_list_sessions(page, page_size, status, user_id)
    return {"records": records, "total": total, "page": max(1, page), "page_size": min(100, max(1, page_size))}


@router.get("/summary")
def get_admin_session_summary(admin: CurrentAdmin = Depends(get_current_admin)):
    return admin_session_summary()

@router.get("/{thread_id}")
def get_admin_session(thread_id: str, admin: CurrentAdmin = Depends(get_current_admin)):
    session = admin_get_session(thread_id)
    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    return session

@router.patch("/{thread_id}")
def update_admin_session(thread_id: str, payload: SessionUpdate, admin: CurrentAdmin = Depends(get_current_admin), x_trace_id: str | None = Header(default=None)):
    updates = {name: getattr(payload, name) for name in payload.model_fields_set}
    if not updates:
        raise HTTPException(status_code=400, detail="at least one field is required")
    try:
        before, after = admin_update_session(thread_id, **updates)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if before is None:
        raise HTTPException(status_code=404, detail="会话不存在")
    action = "handoff" if payload.conversation_status == "transferred" else "update"
    add_audit(thread_id, admin.admin_id, action, before, after, x_trace_id)
    return after
