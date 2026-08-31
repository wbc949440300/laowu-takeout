"""Administrator-only, read-only report Agent endpoint."""
from fastapi import APIRouter, Depends, Header
from pydantic import BaseModel, Field

from app.auth import CurrentAdmin, get_current_admin
from app.tools.report_tools import build_report_tools
from app.tools.sky_client import SkyClient

router = APIRouter(prefix="/admin/report-agent", tags=["admin-report-agent"])


class ReportQuery(BaseModel):
    report_type: str = Field(..., pattern="^(turnover|users|orders|top10)$")
    begin: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")
    end: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")


@router.post("/query")
async def query_report(
    payload: ReportQuery,
    admin: CurrentAdmin = Depends(get_current_admin),
    x_trace_id: str | None = Header(default=None),
):
    client = SkyClient(admin.token, trace_id=x_trace_id, auth_header="token")
    tool = build_report_tools(client)[0]
    import json
    result = json.loads(await tool.ainvoke(payload.model_dump()))
    return result
