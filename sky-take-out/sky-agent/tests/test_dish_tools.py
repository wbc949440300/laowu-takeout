import asyncio
import json

from app.tools.dish_tools import build_dish_tools


class FakeClient:
    trace_id = "trace-test"

    async def get(self, path, params=None):
        if path.startswith("/user/order/orderDetail"):
            return {"orderDetails": [{"name": "红烧肉"}]}
        if path == "/user/category/list":
            return [{"id": 1, "name": "热菜"}]
        return [
            {"id": 1, "name": "红烧肉", "price": 20, "status": 1, "dailyStock": 5},
            {"id": 2, "name": "清炒时蔬", "price": 12, "status": 1, "dailyStock": 0},
            {"id": 3, "name": "鱼香茄子", "price": 16, "status": 1, "dailyStock": None},
        ]


def test_recommendations_filter_purchased_and_sold_out():
    tool = next(t for t in build_dish_tools(FakeClient()) if t.name == "recommend_dishes")
    result = json.loads(asyncio.run(tool.ainvoke({"order_id": 1})))
    assert result["success"] is True
    assert [item["name"] for item in result["data"]] == ["鱼香茄子"]
