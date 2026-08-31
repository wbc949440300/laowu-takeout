"""Stable JSON envelopes returned by every Agent tool."""
import json
from typing import Any

from app.tools.sky_client import SkyApiError


def tool_ok(data: Any, trace_id: str | None = None) -> str:
    return json.dumps(
        {
            "success": True,
            "data": data,
            "error": None,
            "retryable": False,
            "trace_id": trace_id,
        },
        ensure_ascii=False,
        default=str,
    )


def tool_error(error: Exception, trace_id: str | None = None) -> str:
    if isinstance(error, SkyApiError):
        error_data = {"code": error.code, "message": error.msg}
        retryable = error.retryable
        trace_id = error.trace_id or trace_id
    else:
        error_data = {"code": "TOOL_ERROR", "message": "工具执行失败，请稍后重试"}
        retryable = False

    return json.dumps(
        {
            "success": False,
            "data": None,
            "error": error_data,
            "retryable": retryable,
            "trace_id": trace_id,
        },
        ensure_ascii=False,
    )
