"""商家完整操作流验证：接单 → 派送 → 完成，并核对工作台今日数据"""
import httpx, json

BASE = "http://localhost:8080"

# 1. 管理端登录拿 token
with httpx.Client(timeout=20) as c:
    login = c.post(f"{BASE}/admin/employee/login", json={"username": "admin", "password": "123456"}).json()
if login.get("code") != 1:
    print("登录失败:", login)
    raise SystemExit
token = login["data"]["token"]
H = {"token": token}
print("[1] 管理端登录成功")

# 2. 找一个待接单订单
with httpx.Client(timeout=20) as c:
    page = c.get(f"{BASE}/admin/order/conditionSearch", params={"page": 1, "pageSize": 20, "status": 2}, headers=H).json()
records = (page.get("data") or {}).get("records") or []
if not records:
    print("[!] 没有待接单订单，流程结束")
    raise SystemExit
oid = records[0]["id"]
print(f"[2] 找到待接单订单 id={oid} number={records[0].get('number')}")

with httpx.Client(timeout=20) as c:
    # 3. 接单
    r = c.put(f"{BASE}/admin/order/confirm", json={"id": oid, "status": 3}, headers=H).json()
    print(f"[3] 接单: code={r.get('code')} msg={r.get('msg')}")
    # 4. 派送（指派骑手1）
    r = c.put(f"{BASE}/admin/order/delivery/{oid}", params={"riderId": 1}, headers=H).json()
    print(f"[4] 派送: code={r.get('code')} msg={r.get('msg')}")
    # 5. 完成
    r = c.put(f"{BASE}/admin/order/complete/{oid}", headers=H).json()
    print(f"[5] 完成: code={r.get('code')} msg={r.get('msg')}")
    # 6. 核对订单最终状态
    detail = c.get(f"{BASE}/admin/order/details/{oid}", headers=H).json()
    st = (detail.get("data") or {}).get("status")
    print(f"[6] 订单最终状态 status={st} (5=已完成)")
    # 7. 工作台今日数据
    biz = c.get(f"{BASE}/admin/workspace/businessData", headers=H).json()
    print(f"[7] 工作台今日数据: {json.dumps(biz.get('data'), ensure_ascii=False)}")
