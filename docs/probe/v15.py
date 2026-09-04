# -*- coding: utf-8 -*-
import re

data = open(r"D:\workdoc\app\campus-app\docs\probe\xg_app.js", encoding="utf-8", errors="ignore").read()

for kw in ["qingjia", "leave/getapply", "请假申请"]:
    i = data.find(kw)
    if i > 0:
        print("===", kw, "===")
        print(data[max(0, i - 350): i + 350].replace("\n", " ")[:700])
        print()

routes = re.findall(r'path:\s*"(/[^"]{2,50})"', data)
print("=== 路由表 ===", len(routes))
seen = set()
for r in routes:
    if r not in seen:
        seen.add(r)
        print("  ", r)

# 请假字样上下文
i = data.find("请假")
if i > 0:
    print("\n=== 请假字样 ===")
    print(data[max(0, i - 250): i + 250].replace("\n", " ")[:500])
