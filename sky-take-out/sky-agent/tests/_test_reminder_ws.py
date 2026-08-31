"""端到端验证：管理端连 WebSocket → 用户催单 → 管理端收到推送"""
import asyncio, datetime, json
import httpx, jwt, websockets

BASE = "http://localhost:8080"
token = jwt.encode({"userId": 4, "iss": "sky-take-out", "aud": "sky-user",
                    "exp": datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)},
                   "dev-user-jwt-secret-change-before-production-2026", algorithm="HS256")
H = {"authentication": token}

# 1. 找一个当前用户的订单（催单只校验归属+频控）
with httpx.Client(timeout=20) as c:
    recs = c.get(f"{BASE}/user/order/historyOrders", params={"page": 1, "pageSize": 20}, headers=H).json()
orders = (recs.get("data") or {}).get("records") or []
print("候选订单:", [(o.get("id"), o.get("status")) for o in orders[:6]])

async def main():
    picked = None
    async with websockets.connect("ws://localhost:8080/ws/admin_web_test") as ws:
        print("[管理端] WebSocket 已连接 ws://localhost:8080/ws/admin_web_test")
        # 2. 逐个尝试催单（避开 60 秒频控），成功一个就停
        with httpx.Client(timeout=20) as c:
            for o in orders:
                oid = o.get("id")
                r = c.get(f"{BASE}/user/order/reminder/{oid}", headers=H)
                body = r.json()
                if body.get("code") == 1:
                    picked = oid
                    print(f"[用户] 催单成功 订单id={oid}")
                    break
                else:
                    print(f"[用户] 订单{oid} 催单失败: {body.get('msg')}（换下一个）")
        if picked is None:
            print("!! 所有订单都在频控内，无法触发催单")
            return
        # 3. 等待 WebSocket 推送
        try:
            msg = await asyncio.wait_for(ws.recv(), timeout=8)
            print("[管理端] 收到推送 ->", msg)
            data = json.loads(msg)
            if data.get("type") == 2:
                print(">>> 验证通过：管理端成功收到客户催单通知（type=2）")
        except asyncio.TimeoutError:
            print("!! 8秒内未收到 WebSocket 推送")

asyncio.run(main())
