import pytest
from fastapi import HTTPException

from app.api.chat import ChatRequest, _interrupt_expired, _resolve_thread_id, _validate_chat_request, _validate_resume_interrupt


def test_new_thread_is_scoped_to_current_user():
    thread_id = _resolve_thread_id(42, None)
    assert thread_id.startswith("user:42:")


def test_existing_thread_for_current_user_is_accepted():
    assert _resolve_thread_id(42, "user:42:abc-123") == "user:42:abc-123"


@pytest.mark.parametrize("thread_id", [
    "user:7:abc-123",
    "abc-123",
    "user:42:",
    "user:42:../other",
])
def test_foreign_or_invalid_thread_is_rejected(thread_id):
    with pytest.raises(HTTPException) as exc_info:
        _resolve_thread_id(42, thread_id)
    assert exc_info.value.status_code == 403


def test_resume_requires_pending_interrupt():
    with pytest.raises(HTTPException) as exc_info:
        _validate_resume_interrupt(None)
    assert exc_info.value.status_code == 409


def test_expired_interrupt_is_rejected():
    with pytest.raises(HTTPException) as exc_info:
        _validate_resume_interrupt({"expires_at": 0})
    assert exc_info.value.status_code == 409


def test_active_interrupt_can_resume():
    _validate_resume_interrupt({"expires_at": 4102444800})


def test_interrupt_expired_detection():
    assert _interrupt_expired({"expires_at": 0})
    assert _interrupt_expired({})
    assert not _interrupt_expired({"expires_at": 4102444800})


def test_stream_request_rejects_empty_message():
    with pytest.raises(HTTPException) as exc_info:
        _validate_chat_request(ChatRequest(message=""))
    assert exc_info.value.status_code == 400
