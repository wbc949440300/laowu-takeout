"""今日下单并完成，让工作台今日营业额出数"""
import httpx, jwt, datetime, json

BASE = "http://localhost:8080"
now = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)
user_token = jwt.encode({"userId": 4, "iss": "sky-take-out", "aud": "sky-user",
                         "exp": now}, "dev-user-jwt-secret-change-before-production-2026", algorithm="HS256")
UH = {"authentication": user_token}

# 管理端登录
with httpx.Client(timeout=20) as c:
    admin_token = c.post(f"{BASE}/admin/employee/login", json={"username": "admin", "password": "123456"}).json()["data"]["token"]
AH = {"token": admin_token}
print("[1] 用户/管理员 令牌就绪")

with httpx.Client(timeout=25) as c:
    # 2. 找一个起售菜品
    dishes = c.get(f"{BASE}/user/dish/list", params={"categoryId": 11}, headers=UH).json()
    dish = None
    if dishes.get("code") == 1 and dishes.get("data"):
        dish = dishes["data"][0]
    if not dish:
        # 兜底：遍历分类找一个
        cats = c.get(f"{BASE}/user/category/list", params={"type": 1}, headers=UH).json().get("data") or []
        for cat in cats:
            ds = c.get(f"{BASE}/user/dish/list", params={"categoryId": cat["id"]}, headers=UH).json().get("data") or []
            if ds:
                dish = ds[0]; break
    print(f"[2] 选用菜品: {dish['name']} ¥{dish.get('price')}")

    # 3. 清购物车 + 加购2份
    c.delete(f"{BASE}/user/shoppingCart/clean", headers=UH)
    c.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=UH)
    c.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=UH)
    cart = c.get(f"{BASE}/user/shoppingCart/list", headers=UH).json()["data"]
    total = sum(float(x["amount"]) * x["number"] for x in cart)
    print(f"[3] 购物车合计 ¥{total}")

    # 4. 地址
    ab = c.get(f"{BASE}/user/addressBook/list", headers=UH).json()
    if ab.get("code") == 1 and ab.get("data"):
        ab_id = ab["data"][0]["id"]
    else:
        c.post(f"{BASE}/user/addressBook", headers=UH, json={"consignee": "吴先生", "phone": "15060474040", "detail": "一号楼101", "isDefault": 1})
        ab_id = c.get(f"{BASE}/user/addressBook/list", headers=UH).json()["data"][0]["id"]

    # 5. 下单
    r = c.post(f"{BASE}/user/order/submit", headers=UH, json={
        "addressBookId": ab_id, "payMethod": 1, "amount": total,
        "deliveryStatus": 1, "tablewareStatus": 1, "tablewareNumber": 1, "packAmount": 0}).json()
    if r.get("code") != 1:
        print("下单失败:", r); raise SystemExit
    order_id, order_no = r["data"]["id"], r["data"]["orderNumber"]
    print(f"[4] 下单成功 id={order_id} number={order_no}")

    # 6. 支付（mock）
    r = c.put(f"{BASE}/user/order/payment", headers=UH, json={"orderNumber": order_no, "payMethod": 1}).json()
    print(f"[5] 支付(mock): code={r.get('code')}")

    # 7. 商家 接单→派送→完成
    r1 = c.put(f"{BASE}/admin/order/confirm", headers=AH, json={"id": order_id, "status": 3}).json()
    r2 = c.put(f"{BASE}/admin/order/delivery/{order_id}", params={"riderId": 1}, headers=AH).json()
    r3 = c.put(f"{BASE}/admin/order/complete/{order_id}", headers=AH).json()
    print(f"[6] 接单{r1.get('code')} 派送{r2.get('code')} 完成{r3.get('code')}")

    # 8. 工作台今日数据
    biz = c.get(f"{BASE}/admin/workspace/businessData", headers=AH).json()["data"]
    print(f"[7] 工作台今日数据: 营业额=¥{biz.get('turnover')} 有效订单={biz.get('validOrderCount')} 客单价=¥{biz.get('unitPrice')}")
