import time

import app.sessions as sessions


def test_session_lifecycle_is_user_scoped(tmp_path, monkeypatch):
    monkeypatch.setattr(sessions, "_db_path", str(tmp_path / "sessions.db"))
    sessions.init_session_store()
    created = sessions.create_session(7)
    assert created["thread_id"].startswith("user:7:")
    assert sessions.get_session(7, created["thread_id"])["user_id"] == 7
    assert sessions.get_session(8, created["thread_id"]) is None
    assert sessions.list_sessions(7)
    assert sessions.delete_session(7, created["thread_id"])
    assert sessions.get_session(7, created["thread_id"]) is None


def test_admin_session_management_and_audit(tmp_path, monkeypatch):
    monkeypatch.setattr(sessions, "_db_path", str(tmp_path / "sessions.db"))
    sessions.init_session_store()
    created = sessions.create_session(9)
    records, total = sessions.admin_list_sessions(status="processing")
    assert total == 1
    assert records[0]["thread_id"] == created["thread_id"]
    before, after = sessions.admin_update_session(created["thread_id"], conversation_status="transferred", owner_id=3, handoff_reason="complex case")
    assert before["conversation_status"] == "processing"
    assert after["conversation_status"] == "transferred"
    assert after["handoff_at"] is not None
    sessions.add_audit(created["thread_id"], 3, "handoff", before, after, "trace-1")
    with sessions._connect() as conn:
        audit = conn.execute("SELECT * FROM agent_session_audit").fetchone()
    assert audit["admin_id"] == 3
    assert audit["trace_id"] == "trace-1"


def test_admin_session_summary_exposes_only_aggregates(tmp_path, monkeypatch):
    monkeypatch.setattr(sessions, "_db_path", str(tmp_path / "sessions.db"))
    sessions.init_session_store()
    first = sessions.create_session(1)
    second = sessions.create_session(2)
    sessions.update_session_feedback(1, first["thread_id"], 5, "private text")
    sessions.update_session_feedback(2, second["thread_id"], 3, None)
    summary = sessions.admin_session_summary()
    assert summary["total"] == 2
    assert summary["rated_count"] == 2
    assert summary["average_rating"] == 4.0
    assert "feedback" not in summary


def test_admin_session_validation(tmp_path, monkeypatch):
    monkeypatch.setattr(sessions, "_db_path", str(tmp_path / "sessions.db"))
    sessions.init_session_store()
    created = sessions.create_session(1)
    try:
        sessions.admin_update_session(created["thread_id"], conversation_status="unknown")
        assert False, "invalid status must fail"
    except ValueError:
        pass


def test_user_feedback_requires_owned_active_unexpired_session(tmp_path, monkeypatch):
    monkeypatch.setattr(sessions, "_db_path", str(tmp_path / "sessions.db"))
    sessions.init_session_store()
    active = sessions.create_session(7)

    updated = sessions.update_session_feedback(7, active["thread_id"], 5, "helpful")
    assert updated["rating"] == 5
    assert updated["feedback"] == "helpful"
    assert sessions.update_session_feedback(8, active["thread_id"], 1, None) is None

    assert sessions.delete_session(7, active["thread_id"])
    assert sessions.update_session_feedback(7, active["thread_id"], 4, None) is None

    expired = sessions.create_session(7)
    with sessions._connect() as conn:
        conn.execute("UPDATE agent_sessions SET expires_at=? WHERE thread_id=?",
                     (time.time() - 1, expired["thread_id"]))
    assert sessions.update_session_feedback(7, expired["thread_id"], 3, None) is None


def test_admin_can_clear_owner_explicitly(tmp_path, monkeypatch):
    monkeypatch.setattr(sessions, "_db_path", str(tmp_path / "sessions.db"))
    sessions.init_session_store()
    created = sessions.create_session(2)
    sessions.admin_update_session(created["thread_id"], owner_id=9)

    _, after = sessions.admin_update_session(created["thread_id"], owner_id=None)

    assert after["owner_id"] is None
    try:
        sessions.admin_update_session(created["thread_id"], rating=6)
        assert False, "invalid rating must fail"
    except ValueError:
        pass
