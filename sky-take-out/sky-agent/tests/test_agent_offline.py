"""评估集完整性校验 + 工具清单测试（不依赖 Java 后端与 LLM）"""
from pathlib import Path

import yaml


def test_eval_cases_valid():
    path = Path(__file__).resolve().parent / "eval_cases.yaml"
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    cases = data["cases"]
    assert len(cases) >= 10, "评估用例不足"
    ids = [c["id"] for c in cases]
    assert len(ids) == len(set(ids)), "用例 id 重复"
    for c in cases:
        assert c.get("input") and c.get("expect"), f"用例 {c['id']} 缺少 input/expect"


def test_eval_cases_cover_business_and_security_regressions():
    path = Path(__file__).resolve().parent / "eval_cases.yaml"
    cases = yaml.safe_load(path.read_text(encoding="utf-8"))["cases"]
    categories = {case["category"] for case in cases}
    required = {"查订单", "取消订单", "退款咨询", "安全", "越权防护", "注入防护", "菜品咨询"}
    assert required <= categories
    expectations = "\n".join(case["expect"] for case in cases)
    for marker in ("不编造", "确认", "HTTP 401", "不泄露"):
        assert marker in expectations


def test_tool_inventory():
    """工具清单：订单 7 + 店铺 4 + FAQ 1 = 12"""
    from app.tools.dish_tools import build_dish_tools
    from app.tools.faq_tools import build_faq_tools
    from app.tools.order_tools import build_order_tools
    from app.tools.sky_client import SkyClient

    client = SkyClient("test-token")
    tools = build_order_tools(client) + build_dish_tools(client) + build_faq_tools()
    names = {t.name for t in tools}
    assert len(tools) == 13
    assert "cancel_order" in names
    assert "search_faq" in names
    assert "repetition_order" in names
    assert "recommend_dishes" in names


def test_dangerous_tools_guarded():
    """取消和退款必须走确认节点。"""
    from app.graphs.customer_service import DANGEROUS_TOOLS
    assert "cancel_order" in DANGEROUS_TOOLS
    assert "apply_refund" in DANGEROUS_TOOLS
    assert "repetition_order" in DANGEROUS_TOOLS


def test_side_effect_batch_is_split():
    from app.graphs.customer_service import route_tool_calls

    calls = [
        {"name": "query_user_orders", "id": "read", "args": {}},
        {"name": "cancel_order", "id": "write", "args": {"order_id": 1}},
    ]
    assert route_tool_calls(calls) == "split"
    assert route_tool_calls([calls[1]]) == "confirm"


def test_tool_response_envelope():
    import json

    from app.tools.tool_response import tool_ok

    payload = json.loads(tool_ok({"value": 1}, "trace-1"))
    assert payload == {
        "success": True,
        "data": {"value": 1},
        "error": None,
        "retryable": False,
        "trace_id": "trace-1",
    }
