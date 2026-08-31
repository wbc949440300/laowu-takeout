"""SQLite-backed user-scoped session metadata."""
import os
import sqlite3
import threading
import time
from pathlib import Path
from uuid import uuid4

from app.config import get_settings

_lock = threading.RLock()
_db_path = os.environ.get("CHECKPOINT_DB", str(Path(__file__).resolve().parent.parent / "checkpoints.db"))
_UNSET = object()


def _connect():
    conn = sqlite3.connect(_db_path, timeout=5)
    conn.row_factory = sqlite3.Row
    return conn


def init_session_store() -> None:
    with _lock, _connect() as conn:
        conn.execute("""CREATE TABLE IF NOT EXISTS agent_sessions (
            thread_id TEXT PRIMARY KEY,
            user_id INTEGER NOT NULL,
            created_at REAL NOT NULL,
            last_access_at REAL NOT NULL,
            expires_at REAL NOT NULL,
            status TEXT NOT NULL DEFAULT 'active'
        )""")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_agent_sessions_user ON agent_sessions(user_id)")
        columns = {row[1] for row in conn.execute("PRAGMA table_info(agent_sessions)").fetchall()}
        migrations = {
            "conversation_status": "TEXT NOT NULL DEFAULT 'processing'",
            "owner_id": "INTEGER",
            "handoff_at": "REAL",
            "handoff_reason": "TEXT",
            "rating": "INTEGER",
            "feedback": "TEXT",
            "updated_at": "REAL",
        }
        for name, definition in migrations.items():
            if name not in columns:
                conn.execute(f"ALTER TABLE agent_sessions ADD COLUMN {name} {definition}")
        conn.execute("UPDATE agent_sessions SET updated_at=COALESCE(updated_at,last_access_at) WHERE updated_at IS NULL")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_agent_sessions_status ON agent_sessions(conversation_status, updated_at)")
        conn.execute("""CREATE TABLE IF NOT EXISTS agent_session_audit (
            id INTEGER PRIMARY KEY AUTOINCREMENT, thread_id TEXT NOT NULL, admin_id INTEGER NOT NULL,
            action TEXT NOT NULL, before_json TEXT, after_json TEXT, trace_id TEXT, created_at REAL NOT NULL
        )""")


def cleanup_expired() -> int:
    now = time.time()
    with _lock, _connect() as conn:
        result = conn.execute("UPDATE agent_sessions SET status='expired' WHERE expires_at <= ? AND status='active'", (now,))
        return result.rowcount


def create_session(user_id: int) -> dict:
    cleanup_expired()
    now = time.time()
    thread_id = f"user:{user_id}:{uuid4().hex}"
    expires = now + get_settings().session_ttl_seconds
    with _lock, _connect() as conn:
        conn.execute("INSERT INTO agent_sessions(thread_id,user_id,created_at,last_access_at,expires_at,status,conversation_status,updated_at) VALUES(?,?,?,?,?,'active','processing',?)",
                     (thread_id, user_id, now, now, expires, now))
        rows = conn.execute("SELECT thread_id FROM agent_sessions WHERE user_id=? AND status='active' ORDER BY last_access_at DESC",
                            (user_id,)).fetchall()
        for row in rows[get_settings().max_sessions_per_user:]:
            conn.execute("UPDATE agent_sessions SET status='deleted' WHERE thread_id=?", (row[0],))
    return {"thread_id": thread_id, "user_id": user_id, "created_at": now, "last_access_at": now,
            "expires_at": expires, "status": "active", "conversation_status": "processing",
            "owner_id": None, "handoff_at": None, "handoff_reason": None,
            "rating": None, "feedback": None, "updated_at": now}


def get_session(user_id: int, thread_id: str) -> dict | None:
    cleanup_expired()
    with _lock, _connect() as conn:
        row = conn.execute("SELECT * FROM agent_sessions WHERE thread_id=? AND user_id=? AND status='active' AND expires_at>?",
                           (thread_id, user_id, time.time())).fetchone()
        if row is None:
            return None
        now = time.time()
        expires = now + get_settings().session_ttl_seconds
        conn.execute("UPDATE agent_sessions SET last_access_at=?, expires_at=?, updated_at=? WHERE thread_id=?", (now, expires, now, thread_id))
        data = dict(row)
        data.update(last_access_at=now, expires_at=expires)
        return data


def list_sessions(user_id: int) -> list[dict]:
    cleanup_expired()
    with _lock, _connect() as conn:
        rows = conn.execute("SELECT * FROM agent_sessions WHERE user_id=? AND status='active' AND expires_at>? ORDER BY last_access_at DESC",
                            (user_id, time.time())).fetchall()
        return [dict(row) for row in rows]


def delete_session(user_id: int, thread_id: str) -> bool:
    with _lock, _connect() as conn:
        result = conn.execute("UPDATE agent_sessions SET status='deleted' WHERE thread_id=? AND user_id=? AND status='active'",
                             (thread_id, user_id))
        return result.rowcount == 1


def admin_list_sessions(page: int = 1, page_size: int = 20, status: str | None = None, user_id: int | None = None) -> tuple[list[dict], int]:
    init_session_store()
    page = max(1, page); page_size = min(100, max(1, page_size))
    clauses, params = ["1=1"], []
    if status:
        clauses.append("conversation_status=?"); params.append(status)
    if user_id is not None:
        clauses.append("user_id=?"); params.append(user_id)
    where = " AND ".join(clauses)
    with _lock, _connect() as conn:
        total = conn.execute(f"SELECT COUNT(*) FROM agent_sessions WHERE {where}", params).fetchone()[0]
        rows = conn.execute(f"SELECT * FROM agent_sessions WHERE {where} ORDER BY updated_at DESC LIMIT ? OFFSET ?", params + [page_size, (page-1)*page_size]).fetchall()
        return [dict(row) for row in rows], total


def admin_get_session(thread_id: str) -> dict | None:
    init_session_store()
    with _lock, _connect() as conn:
        row = conn.execute("SELECT * FROM agent_sessions WHERE thread_id=?", (thread_id,)).fetchone()
        return dict(row) if row else None


def admin_session_summary() -> dict:
    """Return aggregate feedback/session metrics without exposing conversation text."""
    init_session_store()
    with _lock, _connect() as conn:
        total = conn.execute("SELECT COUNT(*) FROM agent_sessions").fetchone()[0]
        status_rows = conn.execute(
            "SELECT conversation_status, COUNT(*) AS count FROM agent_sessions "
            "GROUP BY conversation_status ORDER BY conversation_status"
        ).fetchall()
        rated, average = conn.execute(
            "SELECT COUNT(rating), AVG(rating) FROM agent_sessions WHERE rating IS NOT NULL"
        ).fetchone()
        return {
            "total": total,
            "by_status": {row[0]: row[1] for row in status_rows},
            "rated_count": rated,
            "average_rating": round(average, 2) if average is not None else None,
        }


def update_session_feedback(user_id: int, thread_id: str, rating: int, feedback: str | None) -> dict | None:
    if not 1 <= rating <= 5:
        raise ValueError("rating must be between 1 and 5")
    now = time.time()
    with _lock, _connect() as conn:
        result = conn.execute(
            """UPDATE agent_sessions SET rating=?, feedback=?, updated_at=?
               WHERE thread_id=? AND user_id=? AND status='active' AND expires_at>?""",
            (rating, feedback, now, thread_id, user_id, now),
        )
        if result.rowcount != 1:
            return None
        return dict(conn.execute("SELECT * FROM agent_sessions WHERE thread_id=?", (thread_id,)).fetchone())


def admin_update_session(thread_id: str, *, conversation_status=_UNSET, owner_id=_UNSET,
                         handoff_reason=_UNSET, rating=_UNSET, feedback=_UNSET) -> tuple[dict | None, dict | None]:
    init_session_store()
    allowed = {"processing", "resolved", "transferred"}
    if conversation_status is not _UNSET and conversation_status not in allowed:
        raise ValueError("invalid conversation status")
    if rating is not _UNSET and rating is not None and not 1 <= rating <= 5:
        raise ValueError("rating must be between 1 and 5")
    with _lock, _connect() as conn:
        row = conn.execute("SELECT * FROM agent_sessions WHERE thread_id=?", (thread_id,)).fetchone()
        if not row: return None, None
        before = dict(row); now = time.time(); fields = []; params = []
        if conversation_status is not _UNSET: fields.append("conversation_status=?"); params.append(conversation_status)
        if owner_id is not _UNSET: fields.append("owner_id=?"); params.append(owner_id)
        if handoff_reason is not _UNSET: fields.append("handoff_reason=?"); params.append(handoff_reason)
        if rating is not _UNSET: fields.append("rating=?"); params.append(rating)
        if feedback is not _UNSET: fields.append("feedback=?"); params.append(feedback)
        if conversation_status == "transferred": fields.append("handoff_at=?"); params.append(now)
        fields.append("updated_at=?"); params.append(now); params.append(thread_id)
        conn.execute(f"UPDATE agent_sessions SET {', '.join(fields)} WHERE thread_id=?", params)
        after = dict(conn.execute("SELECT * FROM agent_sessions WHERE thread_id=?", (thread_id,)).fetchone())
        return before, after


def add_audit(thread_id: str, admin_id: int, action: str, before: dict | None, after: dict | None, trace_id: str | None = None) -> None:
    import json
    with _lock, _connect() as conn:
        conn.execute("INSERT INTO agent_session_audit(thread_id,admin_id,action,before_json,after_json,trace_id,created_at) VALUES(?,?,?,?,?,?,?)",
                     (thread_id, admin_id, action, json.dumps(before, ensure_ascii=False, default=str), json.dumps(after, ensure_ascii=False, default=str), trace_id, time.time()))
