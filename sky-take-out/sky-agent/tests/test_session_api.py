import jwt
from fastapi import FastAPI
from fastapi.testclient import TestClient

import app.sessions as sessions
from app.api.admin_sessions import router as admin_router
from app.api.chat import router as chat_router
from app.config import get_settings


def _token(claim: str, value: int, audience: str, secret: str) -> str:
    settings = get_settings()
    return jwt.encode(
        {claim: value, "iss": settings.jwt_issuer, "aud": audience, "exp": 4102444800},
        secret,
        algorithm="HS256",
    )


def _client(tmp_path, monkeypatch):
    monkeypatch.setattr(sessions, "_db_path", str(tmp_path / "sessions.db"))
    sessions.init_session_store()
    app = FastAPI()
    app.include_router(chat_router, prefix="/agent")
    app.include_router(admin_router)
    return TestClient(app)


def test_feedback_api_enforces_owner_status_and_rating(tmp_path, monkeypatch):
    client = _client(tmp_path, monkeypatch)
    settings = get_settings()
    token7 = _token("userId", 7, settings.jwt_user_audience, settings.user_jwt_secret)
    token8 = _token("userId", 8, settings.jwt_user_audience, settings.user_jwt_secret)
    created = sessions.create_session(7)
    path = f"/agent/sessions/{created['thread_id']}/feedback"

    assert client.patch(path, headers={"authentication": token8}, json={"rating": 5}).status_code == 403
    assert client.patch(path, headers={"authentication": token7}, json={"rating": 0}).status_code == 422
    response = client.patch(path, headers={"authentication": token7}, json={"rating": 5, "feedback": "ok"})
    assert response.status_code == 200
    assert response.json()["rating"] == 5

    sessions.delete_session(7, created["thread_id"])
    assert client.patch(path, headers={"authentication": token7}, json={"rating": 4}).status_code == 404


def test_admin_api_auth_empty_patch_and_owner_clear(tmp_path, monkeypatch):
    client = _client(tmp_path, monkeypatch)
    settings = get_settings()
    user_token = _token("userId", 7, settings.jwt_user_audience, settings.user_jwt_secret)
    admin_token = _token("empId", 3, settings.jwt_admin_audience, settings.admin_jwt_secret)
    created = sessions.create_session(7)
    path = f"/admin/sessions/{created['thread_id']}"

    assert client.patch(path, json={"owner_id": 3}).status_code == 401
    assert client.patch(path, headers={"token": user_token}, json={"owner_id": 3}).status_code == 401
    assert client.patch(path, headers={"token": admin_token}, json={}).status_code == 400
    assert client.patch(path, headers={"token": admin_token}, json={"owner_id": 3}).status_code == 200
    cleared = client.patch(path, headers={"token": admin_token}, json={"owner_id": None})
    assert cleared.status_code == 200
    assert cleared.json()["owner_id"] is None


def test_admin_summary_requires_admin_and_returns_aggregate(tmp_path, monkeypatch):
    client = _client(tmp_path, monkeypatch)
    settings = get_settings()
    admin_token = _token("empId", 3, settings.jwt_admin_audience, settings.admin_jwt_secret)
    sessions.create_session(7)
    assert client.get("/admin/sessions/summary").status_code == 401
    response = client.get("/admin/sessions/summary", headers={"token": admin_token})
    assert response.status_code == 200
    assert response.json()["total"] == 1
    assert "feedback" not in response.json()
