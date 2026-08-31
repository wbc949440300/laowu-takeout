import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi import FastAPI
from fastapi.testclient import TestClient
import jwt

from app.api.admin_reports import router
from app.config import get_settings


def _admin_token():
    settings = get_settings()
    return jwt.encode(
        {"empId": 3, "iss": settings.jwt_issuer, "aud": settings.jwt_admin_audience, "exp": 4102444800},
        settings.admin_jwt_secret,
        algorithm="HS256",
    )


def test_report_agent_requires_admin_token():
    app = FastAPI()
    app.include_router(router)
    client = TestClient(app)
    response = client.post(
        "/admin/report-agent/query",
        json={"report_type": "turnover", "begin": "2026-01-01", "end": "2026-01-02"},
    )
    assert response.status_code == 401


def test_report_agent_rejects_unknown_report_type_at_api_boundary():
    app = FastAPI()
    app.include_router(router)
    client = TestClient(app)
    response = client.post(
        "/admin/report-agent/query",
        headers={"token": _admin_token()},
        json={"report_type": "refunds", "begin": "2026-01-01", "end": "2026-01-02"},
    )
    assert response.status_code == 422
