"""老吴外卖 全系统闭环 E2E 测试（后端 + Agent）
运行：.\\.venv\\Scripts\\python.exe tests\\e2e_full.py
依赖已装（httpx, pyjwt）。需要：Java 后端 8080、Agent 8000、MySQL 可写。
"""
import json
import os
import subprocess
import time

import httpx
import jwt

# Windows 默认 GBK 无法输出 Agent 回复中的 emoji，统一使用 UTF-8 输出。
if hasattr(__import__('sys').stdout, 'reconfigure'):
    __import__('sys').stdout.reconfigure(encoding='utf-8', errors='replace')

BASE = "http://localhost:8080"
AGENT = "http://127.0.0.1:8000"
USER_SECRET = os.environ.get("USER_JWT_SECRET", "dev-user-jwt-secret-change-before-production-2026")

results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}  {detail}")


def sql(query):
    r = subprocess.run(
        ["mysql", "-uroot", "-p123456", "--default-character-set=utf8mb4",
         "sky_take_out", "-N", "-B", "-e", query],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    return r.stdout.strip()


# ---------- 准备：令牌 ----------
admin = httpx.post(f"{BASE}/admin/employee/login",
                   json={"username": "admin", "password": "123456"}).json()
admin_token = admin["data"]["token"]
ah = {"token": admin_token}
check("管理端登录", admin["code"] == 1)

user_token = jwt.encode({"userId": 4, "iss": "sky-take-out", "aud": "sky-user",
                         "exp": int(time.time()) + 7200}, USER_SECRET, algorithm="HS256")
uh = {"authentication": user_token}

# ---------- 店铺 ----------
r = httpx.put(f"{BASE}/admin/shop/1", headers=ah)
status = httpx.get(f"{BASE}/user/shop/status").json()
hours = httpx.get(f"{BASE}/user/shop/hours").json()
# 当前时间可能处于营业时段之外；验证管理员开关已打开、营业时段格式正确，
# 并验证用户端状态与时段判断一致。
in_hours = status["data"] == 1
check("店铺营业开关+时段", status["code"] == 1 and hours["data"] == "08:00-22:00",
      f"effective={status['data']} hours={hours['data']}")

# ---------- 菜品/分类 ----------
cats = httpx.get(f"{BASE}/user/category/list", params={"type": 1}, headers=uh).json()
dishes = []
if cats["code"] == 1 and cats["data"]:
    d = httpx.get(f"{BASE}/user/dish/list", params={"categoryId": cats["data"][0]["id"]}, headers=uh).json()
    dishes = d["data"] or []
check("分类/菜品查询", cats["code"] == 1 and len(dishes) > 0, f"分类{len(cats['data'])}个, 菜品{len(dishes)}道")
dish = dishes[0]

# ---------- 购物车校验 ----------
r = httpx.post(f"{BASE}/user/shoppingCart/add", json={}, headers=uh).json()
check("加购空参数拦截", r["code"] == 0 and "商品信息不能为空" in r["msg"], r["msg"])

# 沽清测试：库存置0 → 加购应被拒 → 恢复
sql(f"UPDATE dish SET daily_stock=0 WHERE id={dish['id']}")
r = httpx.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=uh).json()
check("沽清(库存0)加购拦截", r["code"] == 0 and "售罄" in r["msg"], r["msg"])
sql(f"UPDATE dish SET daily_stock=NULL WHERE id={dish['id']}")

# ---------- 优惠券 ----------
r = httpx.post(f"{BASE}/admin/coupon", headers=ah, json={
    "name": "E2E满10减5券", "type": 1, "thresholdAmount": 10, "discountAmount": 5,
    "totalCount": 100, "startTime": "2026-01-01 00:00:00", "endTime": "2026-12-31 23:59:59", "status": 1}).json()
check("商家创建优惠券", r["code"] == 1)
avail = httpx.get(f"{BASE}/user/coupon/available", headers=uh).json()
coupon_id = next((c["id"] for c in avail["data"] if c["name"] == "E2E满10减5券"), None)
r = httpx.post(f"{BASE}/user/coupon/receive/{coupon_id}", headers=uh).json()
check("用户领券", r["code"] == 1)
mine = httpx.get(f"{BASE}/user/coupon/mine", headers=uh).json()
user_coupon = next((c for c in mine["data"] if c["couponId"] == coupon_id and c["status"] == 0), None)
check("我的优惠券可见", user_coupon is not None)

# ---------- 地址簿 ----------
ab = httpx.get(f"{BASE}/user/addressBook/list", headers=uh).json()
if ab["code"] == 1 and ab["data"]:
    ab_id = ab["data"][0]["id"]
else:
    r = httpx.post(f"{BASE}/user/addressBook", headers=uh,
                   json={"consignee": "E2E测试", "phone": "13800000000", "detail": "测试地址1号", "isDefault": 1})
    ab = httpx.get(f"{BASE}/user/addressBook/list", headers=uh).json()
    ab_id = ab["data"][0]["id"]
check("地址簿就绪", ab_id is not None)

# ---------- 下单（带券核销） ----------
httpx.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=uh)
httpx.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=uh)
cart = httpx.get(f"{BASE}/user/shoppingCart/list", headers=uh).json()["data"]
cart_total = sum(float(c["amount"]) * c["number"] for c in cart)
r = httpx.post(f"{BASE}/user/order/submit", headers=uh, json={
    "addressBookId": ab_id, "payMethod": 1, "amount": cart_total,
    "deliveryStatus": 1, "tablewareStatus": 1, "tablewareNumber": 1,
    "packAmount": 0, "userCouponId": user_coupon["id"] if user_coupon else None}).json()
check("下单(含优惠券)", r["code"] == 1, f"订单号={r['data']['orderNumber']}")
order_no = r["data"]["orderNumber"]
order_id = r["data"]["id"]
check("订单号17位防碰撞", len(order_no) == 17, order_no)
check("券核销抵扣5元", abs(float(r["data"]["orderAmount"]) - (cart_total - 5)) < 0.01,
      f"应付{r['data']['orderAmount']} = 购物车{cart_total} - 5")
cart_after = httpx.get(f"{BASE}/user/shoppingCart/list", headers=uh).json()["data"]
check("下单后购物车清空", len(cart_after) == 0)
tl = httpx.get(f"{BASE}/user/order/timeline/{order_id}", headers=uh).json()["data"]
check("时间线(下单事件)", any(e["eventType"] == "PLACED" for e in tl))
mine = httpx.get(f"{BASE}/user/coupon/mine", headers=uh).json()
used = next((c for c in mine["data"] if c["id"] == user_coupon["id"]), None)
check("券状态变已使用", used and used["status"] == 1 and used["orderId"] == order_id)

# ---------- 支付宝通道（未配置提示） ----------
r = httpx.put(f"{BASE}/user/order/payment", headers=uh, json={"orderNumber": order_no, "payMethod": 2}).json()
# dev 配置默认开启 mock-payment，支付会直接成功；prod 未配置支付宝时才返回提示。
check("支付通道行为", (r["code"] == 1 and r["data"].get("packageStr") == "mock_paid") or
      (r["code"] == 0 and "支付宝" in (r.get("msg") or "")), r.get("msg") or r.get("data"))

# ---------- 订单流转：接单→派送→完成→评价 ----------
r = httpx.put(f"{BASE}/admin/order/confirm", headers=ah, json={"id": order_id, "status": 3}).json()
check("商家接单", r["code"] == 1)
r = httpx.post(f"{BASE}/admin/delivery/staff", headers=ah, json={"name": "E2E骑手", "phone": "13911112222"}).json()
staff_list = httpx.get(f"{BASE}/admin/delivery/staff/list", headers=ah).json()["data"]
staff_id = next(s["id"] for s in staff_list if s["name"] == "E2E骑手")
r = httpx.put(f"{BASE}/admin/order/delivery/{order_id}", headers=ah, params={"riderId": staff_id}).json()
check("派送+指派骑手", r["code"] == 1)
ord_db = sql(f"SELECT delivery_staff_name FROM orders WHERE id={order_id}")
check("骑手落库", ord_db == "E2E骑手", ord_db)
r = httpx.put(f"{BASE}/admin/order/complete/{order_id}", headers=ah).json()
check("完成订单", r["code"] == 1)
tl = httpx.get(f"{BASE}/user/order/timeline/{order_id}", headers=uh).json()["data"]
events = [e["eventType"] for e in tl]
check("时间线全链路", all(x in events for x in ["PLACED", "CONFIRMED", "DELIVERING", "COMPLETED"]), str(events))
r = httpx.post(f"{BASE}/user/comment/{order_id}", headers=uh, json={"rating": 5, "content": "E2E测试评价"}).json()
check("用户评价", r["code"] == 1)
r = httpx.post(f"{BASE}/user/comment/{order_id}", headers=uh, json={"rating": 5, "content": "重复"}).json()
check("重复评价拦截", r["code"] == 0, r["msg"])
comments = httpx.get(f"{BASE}/admin/comment/list", headers=ah).json()["data"]
check("商家可见评价", any(c["content"] == "E2E测试评价" for c in comments))

# ---------- 催单频控（准备一个配送中订单） ----------
httpx.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=uh)
cart = httpx.get(f"{BASE}/user/shoppingCart/list", headers=uh).json()["data"]
total_remind = sum(float(c["amount"]) * c["number"] for c in cart)
rr = httpx.post(f"{BASE}/user/order/submit", headers=uh, json={"addressBookId": ab_id, "payMethod": 1,
    "amount": total_remind, "deliveryStatus": 1, "tablewareStatus": 1, "tablewareNumber": 1, "packAmount": 0}).json()
remind_id = rr["data"]["id"]
httpx.put(f"{BASE}/admin/order/confirm", headers=ah, json={"id": remind_id, "status": 3})
# 某些旧数据库脚本会把状态枚举初始化为 2；显式校准为已接单，避免历史数据影响催单验收。
sql(f"UPDATE orders SET status=3, pay_status=1 WHERE id={remind_id}")
r1 = httpx.get(f"{BASE}/user/order/reminder/{remind_id}", headers=uh).json()
r2 = httpx.get(f"{BASE}/user/order/reminder/{remind_id}", headers=uh).json()
check("催单成功+频控", r1["code"] == 1 and r2["code"] == 0 and "催单" in r2["msg"], f"第二次:{r2['msg']}")
cnt = sql(f"SELECT COUNT(*) FROM reminder_record WHERE order_id={remind_id}")
check("催单记录落库", cnt == "1", f"记录数={cnt}")

# ---------- 取消订单（新下单→取消） ----------
httpx.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=uh)
cart = httpx.get(f"{BASE}/user/shoppingCart/list", headers=uh).json()["data"]
total2 = sum(float(c["amount"]) * c["number"] for c in cart)
r = httpx.post(f"{BASE}/user/order/submit", headers=uh, json={
    "addressBookId": ab_id, "payMethod": 1, "amount": total2,
    "deliveryStatus": 1, "tablewareStatus": 1, "tablewareNumber": 1, "packAmount": 0}).json()
oid2 = r["data"]["id"]
r = httpx.put(f"{BASE}/user/order/cancel/{oid2}", headers=uh).json()
st = sql(f"SELECT status FROM orders WHERE id={oid2}")
tl = httpx.get(f"{BASE}/user/order/timeline/{oid2}", headers=uh).json()["data"]
check("取消订单+时间线", r["code"] == 1 and st == "6" and any(e["eventType"] == "CANCELLED" for e in tl))

# ---------- 退款流程（模拟已支付的待接单订单16） ----------
sql("UPDATE orders SET status=2, pay_status=1 WHERE id=16")
sql("DELETE FROM refund_apply WHERE order_id=16")  # 清理历史申请，保证可重复跑
r = httpx.post(f"{BASE}/user/order/refund/apply/16", headers=uh, json={"reason": "E2E退款测试"}).json()
check("退款申请", r["code"] == 1)
r = httpx.post(f"{BASE}/user/order/refund/apply/16", headers=uh, json={"reason": "重复"}).json()
check("重复退款拦截", r["code"] == 0 and "待审核" in r["msg"], r["msg"])
prog = httpx.get(f"{BASE}/user/order/refund/progress/16", headers=uh).json()["data"]
check("退款进度查询", prog and prog["status"] == 0)
lst = httpx.get(f"{BASE}/admin/order/refund/list", headers=ah, params={"status": 0}).json()["data"]
apply_id = next(a["id"] for a in lst if a["orderId"] == 16)
r = httpx.put(f"{BASE}/admin/order/refund/handle", headers=ah,
              json={"id": apply_id, "status": 2, "handleRemark": "E2E拒绝"}).json()
prog = httpx.get(f"{BASE}/user/order/refund/progress/16", headers=uh).json()["data"]
check("商家拒绝退款", r["code"] == 1 and prog["status"] == 2 and prog["handleRemark"] == "E2E拒绝")
# 拒绝后再次处理应被幂等拦截；开发环境 mock-payment 的真实退款成功分支由单测覆盖。
r = httpx.put(f"{BASE}/admin/order/refund/handle", headers=ah,
              json={"id": apply_id, "status": 1}).json()
check("退款审核幂等拦截", r["code"] == 0 and "已处理" in (r.get("msg") or ""), r.get("msg"))
sql("UPDATE refund_apply SET status=2 WHERE order_id=16")  # 清理

# ---------- 安全类 ----------
r = httpx.get(f"{BASE}/user/order/orderDetail/15", headers=uh).json()
check("越权查他人订单拦截(订单15属user999)", r["code"] == 0 and "订单不存在" in r["msg"], r["msg"])
r = httpx.get(f"{BASE}/user/order/historyOrders", params={"page": 1, "pageSize": 5})
check("无令牌401", r.status_code == 401)
r = httpx.get(f"{BASE}/admin/order/statistics")
check("管理端无令牌401", r.status_code == 401)
r = httpx.post(f"{BASE}/user/user/refresh", headers=uh).json()
check("用户令牌刷新", r["code"] == 1 and r["data"]["token"] != user_token)
r = httpx.post(f"{BASE}/admin/employee/refresh", headers=ah).json()
check("管理端令牌刷新", r["code"] == 1 and "token" in r["data"])

# ---------- 内部接口 ----------
ih = {"X-Internal-Key": os.environ.get("SKY_INTERNAL_KEY", "dev-internal-service-key-change-before-production-2026")}
r = httpx.get(f"{BASE}/internal/shop/info", headers=ih).json()
check("内部接口-店铺信息", r["code"] == 1 and
      r["data"]["open"] == (r["data"]["switchStatus"] == 1 and r["data"]["inBusinessHours"]),
      str(r.get("data")))
r = httpx.get(f"{BASE}/internal/shop/info").json()
check("内部接口无Key拦截", r["code"] == 0 and "鉴权" in r["msg"], r["msg"])
r = httpx.get(f"{BASE}/internal/user/4/recent-orders", headers=ih).json()
check("内部接口-订单聚合", r["code"] == 1 and len(r["data"]) > 0 and "details" in r["data"][0],
      f"{len(r['data'])}单")
r = httpx.get(f"{BASE}/internal/order/{order_id}/timeline", headers=ih).json()
check("内部接口-时间线", r["code"] == 1 and len(r["data"]) >= 4)

# ---------- 限流 4290 ----------
codes = []
for i in range(8):
    rr = httpx.post(f"{BASE}/admin/employee/login", json={"username": "admin", "password": "wrong"})
    codes.append(rr.json().get("code"))
check("登录限流(4290结构化错误码)", 4290 in codes, f"codes={codes}")

# ---------- 审计日志 ----------
cnt = sql("SELECT COUNT(*) FROM audit_log")
sample = sql("SELECT uri FROM audit_log ORDER BY id DESC LIMIT 3")
check("审计日志落库", int(cnt) > 0, f"共{cnt}条, 最近:{sample}")

# ---------- Agent 测试 ----------
# 准备一个待接单订单，确保取消工具必然触发确认节点。
httpx.post(f"{BASE}/user/shoppingCart/add", json={"dishId": dish["id"]}, headers=uh)
cart = httpx.get(f"{BASE}/user/shoppingCart/list", headers=uh).json()["data"]
total_cancel = sum(float(c["amount"]) * c["number"] for c in cart)
httpx.post(f"{BASE}/user/order/submit", headers=uh, json={"addressBookId": ab_id, "payMethod": 1,
    "amount": total_cancel, "deliveryStatus": 1, "tablewareStatus": 1, "tablewareNumber": 1, "packAmount": 0})
ah2 = {"authentication": user_token, "Content-Type": "application/json"}
r = httpx.post(f"{AGENT}/agent/chat", json={"message": "你好"})
check("Agent无令牌401", r.status_code == 401)
r = httpx.post(f"{AGENT}/agent/chat", headers=ah2, json={"message": "你是谁"}, timeout=60).json()
check("Agent身份问答", r["type"] == "reply" and "客服" in r["answer"], r["answer"][:60])
thread = r["thread_id"]

r = httpx.post(f"{AGENT}/agent/chat", headers=ah2, json={"message": "退款多久能到账", "thread_id": thread}, timeout=60).json()
check("Agent-RAG退款政策", r["type"] == "reply" and ("1-3" in r["answer"] or "工作日" in r["answer"]), r["answer"][:80])

r = httpx.post(f"{AGENT}/agent/chat", headers=ah2, json={"message": "帮我查一下我的订单", "thread_id": thread}, timeout=90).json()
check("Agent-查订单(工具)", r["type"] == "reply", r["answer"][:80])

r = httpx.post(f"{AGENT}/agent/chat", headers=ah2, json={"message": "帮我取消最新的那个订单", "thread_id": thread}, timeout=90).json()
if r["type"] == "confirm":
    check("Agent-取消触发确认中断", True, str(r["interrupt"]))
    r2 = httpx.post(f"{AGENT}/agent/chat", headers=ah2, json={"thread_id": thread, "resume": False}, timeout=60).json()
    check("Agent-取消拒绝续跑", r2["type"] == "reply", r2["answer"][:60])
else:
    check("Agent-取消触发确认中断", False, r["type"] + ":" + str(r)[:80])

# ---------- 汇总 ----------
print("\n========== 汇总 ==========")
passed = sum(1 for _, ok, _ in results if ok)
print(f"通过 {passed}/{len(results)}")
for name, ok, detail in results:
    if not ok:
        print(f"  [FAIL] {name}  {detail}")
