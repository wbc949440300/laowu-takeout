"""订单相关工具：查询类（只读）+ 催单（低风险）+ 取消（高危，走图内确认节点）
所有接口后端已做归属校验，工具层不传他人身份。"""
from langchain_core.tools import tool

from app.tools.sky_client import SkyClient
from app.tools.tool_response import tool_error, tool_ok

ORDER_STATUS_TEXT = {
    1: "待付款", 2: "待接单", 3: "已接单", 4: "派送中", 5: "已完成", 6: "已取消",
}


def build_order_tools(client: SkyClient):
    """按请求构造工具闭包（绑定当前用户 token）"""

    @tool
    async def query_user_orders(status: int | None = None) -> str:
        """查询当前用户的历史订单列表（默认最近订单）。
        status 可选：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消，不确定就不传。"""
        try:
            data = await client.get(
                "/user/order/historyOrders",
                params={"page": 1, "pageSize": 5, **({"status": status} if status else {})},
            )
            records = (data or {}).get("records", [])
            brief = [
                {
                    "id": r.get("id"),
                    "orderNumber": r.get("number"),
                    "status": ORDER_STATUS_TEXT.get(r.get("status"), r.get("status")),
                    "amount": r.get("amount"),
                    "orderTime": r.get("orderTime"),
                }
                for r in records
            ]
            return tool_ok(brief, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def get_order_detail(order_id: int) -> str:
        """查询指定订单的详情（含菜品明细、状态、金额）。用户问"我的订单"且只有一个候选时使用。"""
        try:
            data = await client.get(f"/user/order/orderDetail/{order_id}")
            if isinstance(data, dict):
                data["status"] = ORDER_STATUS_TEXT.get(data.get("status"), data.get("status"))
            return tool_ok(data, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def get_order_timeline(order_id: int) -> str:
        """查询指定订单的时间线进度（下单/支付/接单/派送/完成/取消等事件）。回答"订单到哪了/进度"优先用它。"""
        try:
            data = await client.get(f"/user/order/timeline/{order_id}")
            return tool_ok(data, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def remind_order(order_id: int) -> str:
        """催单：提醒商家尽快处理/配送指定订单。同一订单 1 分钟内有频控。"""
        try:
            await client.get(f"/user/order/reminder/{order_id}")
            return tool_ok({"result": "催单成功，已通知商家"}, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def cancel_order(order_id: int) -> str:
        """取消指定订单（高危操作，必须先经用户确认）。仅限待付款/待接单状态；已支付会自动发起退款。"""
        try:
            await client.put(f"/user/order/cancel/{order_id}")
            return tool_ok({"result": "订单已取消"}, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def apply_refund(order_id: int, reason: str) -> str:
        """为已支付的订单发起退款申请，交由商家审核。用户表达"退款/不想要了退钱"时使用。"""
        try:
            await client.post(f"/user/order/refund/apply/{order_id}", json={"reason": reason or "用户申请退款"})
            return tool_ok({"result": "退款申请已提交，等待商家审核"}, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def repetition_order(order_id: int) -> str:
        """再来一单：把指定历史订单的商品重新加入购物车（不直接下单，用户可再确认/支付）。
        下架/售罄商品会被自动过滤。用户表达"再来一单/上次那个再买一次"时使用。"""
        try:
            await client.post(f"/user/order/repetition/{order_id}")
            return tool_ok({"result": "商品已加入购物车，请确认后提交订单"}, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    return [query_user_orders, get_order_detail, get_order_timeline,
            remind_order, cancel_order, apply_refund, repetition_order]
