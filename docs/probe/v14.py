# -*- coding: utf-8 -*-
"""v14: 探测学工系统（请假申请入口）"""
import sys, time, random, json, re
import requests
from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad

BASE = "https://ehallmobile.xyc.edu.cn"
API = BASE + "/api/v4/api"
JWXT = "https://zfjwxt.xyc.edu.cn"
MASTER_KEY = b"dc651e062a92599aa1230153"
ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
account, password = sys.argv[1], sys.argv[2]

rk = "".join(random.choice(ALNUM) for _ in range(24)).encode("utf-8")
s = requests.Session()
s.headers.update({"User-Agent": UA, "Referer": BASE + "/mobile/index", "Origin": BASE})
payload = {
    "userDevice": account + str(int(time.time() * 1000)),
    "loginName": DES3.new(rk, DES3.MODE_ECB).encrypt(pad(account.encode(), 8)).hex(),
    "key": DES3.new(MASTER_KEY, DES3.MODE_ECB).encrypt(pad(rk, 8)).hex(),
    "passWord": DES3.new(rk, DES3.MODE_ECB).encrypt(pad(password.encode(), 8)).hex(),
    "loginType": "2",
}
token = s.post(API + "/login", data=payload, timeout=30).json()["token"]
s.headers["Authorization"] = token
print("[token]", token[:20], "...")

# 学工系统 href=https://ssxt.xyc.edu.cn/wiseduIndex.jsp，拼 ticket 打开
url = "https://ssxt.xyc.edu.cn/wiseduIndex.jsp?ticket=" + token
r = s.get(url, allow_redirects=True, timeout=30)
for h in r.history:
    print("  ", h.status_code, "->", h.headers.get("Location", "")[:110])
print("[学工落地]", r.url[:120], "| HTTP", r.status_code, "| len", len(r.text))
page = r.text
with open("D:/workdoc/app/campus-app/docs/probe/xg_index.html", "w", encoding="utf-8") as f:
    f.write(page)

# 找请假相关链接
print("\n=== 请假相关 ===")
hits = re.findall(r'[^<>]{0,80}请假[^<>]{0,80}', page)
for h in hits[:6]:
    print("  ", h.strip()[:150])
print("\n=== 页面链接样例 ===")
for u in sorted(set(re.findall(r'(?:href|src|url)\s*=\s*["\']([^"\']{6,120})["\']', page)))[:25]:
    print("  ", u[:110])
