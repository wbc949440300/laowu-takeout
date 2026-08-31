import json, httpx

# Apifox exported doc
with open(r"c:\Users\wbc94\Desktop\老吴外卖-管理端接口.openapi.json", encoding="utf-8") as f:
    af = json.load(f)
af_paths = set(af.get("paths", {}).keys())

# backend swagger (fresh download, proper encoding)
url = "http://localhost:8080/v2/api-docs"
params = {"group": "管理端接口"}
try:
    r = httpx.get(url, params=params, timeout=20)
    sw = json.loads(r.text)
    sw_paths = set(sw.get("paths", {}).keys())
    ok = True
except Exception as e:
    sw_paths = set()
    ok = False
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
with open(r"d:\IDEA\sky-take-out\_api_diff.txt", "w", encoding="utf-8") as f:
    f.write(out)
print(out)
