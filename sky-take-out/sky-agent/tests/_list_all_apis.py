import json, httpx

base = "http://localhost:8080/v2/api-docs"
for group in ["管理端接口", "用户端接口"]:
    try:
        r = httpx.get(base, params={"group": group}, timeout=20)
        sw = json.loads(r.text)
    except Exception as e:
        print(f"[{group}] fetch error: {e}")
        continue
    paths = sw.get("paths", {})
    print(f"\n########## {group} ({len(paths)} paths) ##########")
    rows = []
    for p, methods in sorted(paths.items()):
        for m, op in methods.items():
            if m in ("get","post","put","delete","patch"):
                summary = op.get("summary","") or op.get("operationId","")
                rows.append(f"{m.upper():6} {p:50} {summary}")
    for row in rows:
        print(row)
