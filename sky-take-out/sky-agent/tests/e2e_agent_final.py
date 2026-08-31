"""Agent 最终验证：401 拦截 + 取消订单确认流（interrupt → resume）"""
import httpx
import jwt
import time

AGENT = "http://127.0.0.1:8000"
token = jwt.encode({"userId": 4, "iss": "sky-take-out", "aud": "sky-user",
                    "exp": int(time.time()) + 7200},
                   "dev-user-jwt-secret-change-before-production-2026", algorithm="HS256")
h = {"authentication": token, "Content-Type": "application/json"}

results = []

# 1. 无令牌 401
r = httpx.post(f"{AGENT}/agent/chat", json={"message": "你好"}, timeout=30)
results.append(("Agent无令牌401", r.status_code == 401, f"HTTP {r.status_code}"))

# 2. 取消订单 → 应触发 confirm 中断
r = httpx.post(f"{AGENT}/agent/chat", headers=h,
               json={"message": "帮我取消订单16"}, timeout=120).json()
thread = r.get("thread_id")
if r["type"] == "confirm":
    results.append(("取消触发确认中断", True, str(r["interrupt"])))
    # 3. 拒绝执行
    r2 = httpx.post(f"{AGENT}/agent/chat", headers=h,
                    json={"thread_id": thread, "resume": False}, timeout=120).json()
    results.append(("拒绝后续跑(不执行)", r2["type"] == "reply", r2.get("answer", "")[:80]))
else:
    results.append(("取消触发确认中断", False, r["type"] + ": " + r.get("answer", str(r))[:80]))

# 4. 查数据库确认订单16未被取消（拒绝后状态仍为2）
import subprocess
st = subprocess.run(["mysql", "-uroot", "-p123456", "--default-character-set=utf8mb4",
                     "sky_take_out", "-N", "-B", "-e", "SELECT status FROM orders WHERE id=16"],
                    capture_output=True, text=True, encoding="utf-8").stdout.strip()
results.append(("拒绝后订单未取消", st == "2", f"status={st}"))

print("\n========== Agent 最终验证 ==========")
for name, ok, detail in results:
    print(f"[{'PASS' if ok else 'FAIL'}] {name}  {detail}")
