import json, httpx

# Apifox exported doc (user-side)
with open(r"c:\Users\wbc94\Desktop\苍穹外卖-用户端接口.openapi.json", encoding="utf-8") as f:
    af = json.load(f)
af_paths = set(af.get("paths", {}).keys())

# backend swagger (user-side group)
url = "http://localhost:8080/v2/api-docs"
params = {"group": "用户端接口"}
try:
    r = httpx.get(url, params=params, timeout=20)
    sw = json.loads(r.text)
    sw_paths = set(sw.get("paths", {}).keys())
except Exception as e:
    sw_paths = set()
    print("swagger fetch error:", e)

missing = sorted(sw_paths - af_paths)   # backend has, Apifox missing
extra = sorted(af_paths - sw_paths)     # Apifox has, backend missing

lines = []
lines.append(f"Apifox paths: {len(af_paths)} | Backend paths: {len(sw_paths)}")
lines.append(f"\n=== Backend has but Apifox MISSING ({len(missing)}):")
lines.extend(missing)
lines.append(f"\n=== Apifox has but backend NOT have ({len(extra)}):")
lines.extend(extra)

out = "\n".join(lines)
print(out)
