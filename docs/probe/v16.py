# -*- coding: utf-8 -*-
import re

data = open(r"D:\workdoc\app\campus-app\docs\probe\xg_app.js", encoding="utf-8", errors="ignore").read()
routes = re.findall(r'path:\s*"(/[^"]{2,60})"', data)
qj = [r for r in dict.fromkeys(routes) if ("qj" in r.lower() or "leave" in r.lower() or "xssq" in r.lower())]
print("请假相关路由:")
for r in qj:
    print("  ", r)

i = data.find("handleClick:function(t)")
print()
print(data[i: i + 900].replace("\n", " ")[:900])
