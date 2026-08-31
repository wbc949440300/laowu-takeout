"""菜品/店铺咨询类工具（全部只读）"""
from langchain_core.tools import tool

from app.tools.sky_client import SkyClient
from app.tools.tool_response import tool_error, tool_ok


def build_dish_tools(client: SkyClient):

    @tool
    async def recommend_dishes(order_id: int | None = None) -> str:
        """基于用户订单偏好推荐菜品，只返回后端确认在售且未售罄的菜品。"""
        try:
            purchased = set()
            if order_id:
                detail = await client.get(f"/user/order/orderDetail/{order_id}") or {}
                for item in detail.get("orderDetails") or detail.get("orderDetailList") or []:
                    if item.get("name"):
                        purchased.add(item["name"])
            categories = await client.get("/user/category/list", params={"type": 1}) or []
            candidates = []
            for category in categories[:10]:
                dishes = await client.get("/user/dish/list", params={"categoryId": category.get("id")}) or []
                for dish in dishes:
                    stock = dish.get("dailyStock")
                    if dish.get("status") not in (None, 1) or stock == 0 or dish.get("name") in purchased:
                        continue
                    candidates.append({"id": dish.get("id"), "name": dish.get("name"),
                                       "price": dish.get("price"), "description": dish.get("description"),
                                       "category": category.get("name"), "stock": stock})
            return tool_ok(candidates[:5], client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def list_dishes(category_id: int | None = None) -> str:
        """查询菜品列表（名称/价格/描述）。不传 category_id 时先取全部分类再汇总。
        回答"有什么菜/招牌菜/多少钱"时使用。价格必须以此工具返回为准，禁止凭记忆回答。"""
        try:
            if category_id:
                data = await client.get("/user/dish/list", params={"categoryId": category_id})
                return tool_ok(data, client.trace_id)
            categories = await client.get("/user/category/list", params={"type": 1}) or []
            result = []
            for cat in categories[:10]:  # 控制调用次数
                dishes = await client.get("/user/dish/list", params={"categoryId": cat.get("id")}) or []
                for d in dishes:
                    result.append({
                        "name": d.get("name"),
                        "price": d.get("price"),
                        "description": d.get("description"),
                        "category": cat.get("name"),
                    })
            return tool_ok(result, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def list_setmeals(category_id: int | None = None) -> str:
        """查询套餐列表（名称/价格）。回答"有什么套餐"时使用。不传 category_id 时遍历所有套餐分类汇总。"""
        try:
            if category_id:
                data = await client.get("/user/setmeal/list", params={"categoryId": category_id})
                return tool_ok([{"name": s.get("name"), "price": s.get("price")} for s in (data or [])], client.trace_id)
            categories = await client.get("/user/category/list", params={"type": 2}) or []
            result = []
            for cat in categories[:10]:  # 遍历所有套餐分类，避免遗漏
                meals = await client.get("/user/setmeal/list", params={"categoryId": cat.get("id")}) or []
                for s in meals:
                    result.append({
                        "name": s.get("name"),
                        "price": s.get("price"),
                        "category": cat.get("name"),
                    })
            return tool_ok(result, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def get_shop_status() -> str:
        """查询店铺当前营业状态（1=营业中 0=打烊）。回答"现在营业吗/开门吗"时使用。"""
        try:
            status = await client.get("/user/shop/status")
            return tool_ok({"open": status == 1, "status": status}, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    @tool
    async def get_shop_hours() -> str:
        """查询店铺营业时段（如 08:00-22:00）。回答"几点开门/几点关门"时使用。"""
        try:
            hours = await client.get("/user/shop/hours")
            return tool_ok({"hours": hours}, client.trace_id)
        except Exception as e:
            return tool_error(e, client.trace_id)

    return [list_dishes, list_setmeals, recommend_dishes, get_shop_status, get_shop_hours]
