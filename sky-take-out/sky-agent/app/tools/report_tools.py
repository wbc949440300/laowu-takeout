"""Read-only administrator business reports backed by fixed Java endpoints."""
from datetime import date, datetime, timezone


from langchain_core.tools import tool

from app.tools.sky_client import SkyClient
from app.tools.tool_response import tool_error, tool_ok

REPORT_ENDPOINTS: dict[str, str] = {
    "turnover": "/admin/report/turnoverStatistics",
    "users": "/admin/report/userStatistics",
    "orders": "/admin/report/ordersStatistics",
    "top10": "/admin/report/top10",
}


def _parse_date(value: str) -> date:
    return datetime.strptime(value, "%Y-%m-%d").date()


def build_report_tools(client: SkyClient):
    @tool
    async def query_business_report(report_type: str, begin: str, end: str) -> str:
        """查询管理员经营报表。仅允许固定只读指标，日期格式为 YYYY-MM-DD，最多一年。"""
        try:
            if report_type not in REPORT_ENDPOINTS:
                raise ValueError("report_type must be one of turnover, users, orders, top10")
            begin_date = _parse_date(begin)
            end_date = _parse_date(end)
            if begin_date > end_date:
                raise ValueError("begin must not be after end")
            if (end_date - begin_date).days > 366:
                raise ValueError("date range must not exceed 366 days")
            endpoint = REPORT_ENDPOINTS[report_type]
            data = await client.get(endpoint, params={"begin": begin, "end": end})
            return tool_ok(
                {
                    "report_type": report_type,
                    "begin": begin,
                    "end": end,
                    "data": data,
                    "source": endpoint,
                    "generated_at": datetime.now(timezone.utc).isoformat(),
                },
                client.trace_id,
            )
        except Exception as exc:
            return tool_error(exc, client.trace_id)

    return [query_business_report]
