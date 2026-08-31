import asyncio
import json

from app.tools.report_tools import build_report_tools


class FakeAdminClient:
    trace_id = "trace-report"

    def __init__(self):
        self.calls = []

    async def get(self, path, params=None):
        self.calls.append((path, params))
        return {"dateList": "2026-01-01", "turnoverList": "100.0"}


def test_business_report_is_read_only_and_has_source():
    client = FakeAdminClient()
    tool = build_report_tools(client)[0]
    result = json.loads(asyncio.run(tool.ainvoke({
        "report_type": "turnover", "begin": "2026-01-01", "end": "2026-01-31"
    })))
    assert result["success"] is True
    assert result["data"]["source"] == "/admin/report/turnoverStatistics"
    assert result["data"]["data"]["turnoverList"] == "100.0"
    assert client.calls == [(
        "/admin/report/turnoverStatistics", {"begin": "2026-01-01", "end": "2026-01-31"}
    )]


def test_business_report_rejects_invalid_range_without_upstream_call():
    client = FakeAdminClient()
    tool = build_report_tools(client)[0]
    result = json.loads(asyncio.run(tool.ainvoke({
        "report_type": "orders", "begin": "2026-02-01", "end": "2026-01-01"
    })))
    assert result["success"] is False
    assert result["error"]["code"] == "TOOL_ERROR"
    assert client.calls == []


def test_business_report_rejects_unknown_type_without_upstream_call():
    client = FakeAdminClient()
    tool = build_report_tools(client)[0]
    result = json.loads(asyncio.run(tool.ainvoke({
        "report_type": "refunds", "begin": "2026-01-01", "end": "2026-01-02"
    })))
    assert result["success"] is False
    assert client.calls == []
