import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import jwt
from fastapi import FastAPI
from fastapi.testclient import TestClient

import app.rag.retriever as retriever
from app.api.admin_faq import router
from app.config import get_settings


def _admin_token():
    settings = get_settings()
    return jwt.encode(
        {"empId": 3, "iss": settings.jwt_issuer, "aud": settings.jwt_admin_audience, "exp": 4102444800},
        settings.admin_jwt_secret,
        algorithm="HS256",
    )


def _client(tmp_path, monkeypatch):
    monkeypatch.setattr(retriever, "KNOWLEDGE_DIR", tmp_path)
    monkeypatch.setattr(retriever, "_ENTRIES", None)
    (tmp_path / "faq.md").write_text("## 退款多久到账？\n微信退款一般 1-3 个工作日到账。", encoding="utf-8")
    app = FastAPI()
    app.include_router(router)
    return TestClient(app), {"token": _admin_token()}


def test_faq_crud_and_cache_reload(tmp_path, monkeypatch):
    client, headers = _client(tmp_path, monkeypatch)
    listed = client.get("/admin/faq", headers=headers)
    assert listed.status_code == 200
    assert listed.json()["total"] == 1
    created = client.post("/admin/faq", headers=headers, json={"question": "支持哪些支付？", "answer": "支持微信和支付宝。"})
    assert created.status_code == 200
    entry_id = created.json()["id"]
    assert any(item["question"] == "支持哪些支付？" for item in client.get("/admin/faq", headers=headers).json()["records"])
    duplicate = client.post("/admin/faq", headers=headers, json={"question": "支持哪些支付？", "answer": "重复"})
    assert duplicate.status_code == 409
    updated = client.put(f"/admin/faq/{entry_id}", headers=headers, json={"question": "支持哪些支付方式？", "answer": "支持微信、支付宝。"})
    assert updated.status_code == 200
    updated_id = updated.json()["id"]
    assert client.delete(f"/admin/faq/{updated_id}", headers=headers).json() == {"deleted": True}
    assert retriever.search("支持哪些支付方式？", min_score=1) == []


def test_faq_requires_admin_and_valid_payload(tmp_path, monkeypatch):
    client, _ = _client(tmp_path, monkeypatch)
    assert client.get("/admin/faq").status_code == 401
    assert client.post("/admin/faq", headers={"token": _admin_token()}, json={"question": "", "answer": "x"}).status_code == 422
    assert client.delete("/admin/faq/missing", headers={"token": _admin_token()}).status_code == 404
