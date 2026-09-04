# -*- coding: utf-8 -*-
"""v10: 探测「学生课表查询(旧)」Y253510 的落地页与数据形态"""
import sys, time, random, json, re
import requests
from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad

BASE = "https://ehallmobile.xyc.edu.cn"
API = BASE + "/api/v4/api"
JWXT = "https://zfjwxt.xyc.edu.cn"
MASTER_KEY = b"dc651e062a92599aa1230153"
ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0"
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
r = s.get(JWXT + "/sso/xyoauthlogin?ticket=" + token, allow_redirects=True, timeout=30)
html = r.text
print("[SSO]", "OK" if "login" not in r.url.lower() else "FAIL")

m = re.search(
    r"clickMenu\('([^']*)','([^']*)','(Y253510)','([^']*)','([^']*)','([^']*)','([^']*)'\)"
    r'[^>]*title="([^"]*)"',
    html)
if not m:
    print("未找到 Y253510 菜单")
    sys.exit(1)
procode, typ, choice, uid, role, key, ts, title = m.groups()
print("[菜单]", title, choice)

wap = (JWXT + f"/jwglxt/xtgl/login_wapLogin.html?procode={procode}&type={typ}"
       f"&choice={choice}&uid={uid}&role={role}&key={key}&time={ts}")
r2 = s.get(wap, allow_redirects=True, timeout=30)
page = r2.text
print("[落地页]", r2.url[:110])
print("[状态]", r2.status_code, "长度", len(page))

with open("D:/workdoc/app/campus-app/docs/probe/kb_old_page.html", "w", encoding="utf-8") as f:
    f.write(page)

# 页面结构分析
print("\n=== 页面中的表格 ===")
print("  <table> 数量:", len(re.findall(r"<table", page)))
print("  课表关键字出现:", {k: page.count(k) for k in ["kcmc", "xqj", "节", "大学英语", "kbList", "xskb_list"]})
print("\n=== 页面中的接口/JS ===")
for u in sorted(set(re.findall(r'["\'](/[A-Za-z][A-Za-z0-9_/]+\.html[^"\']*)["\']', page)))[:15]:
    print("  ", u[:100])
print("\n=== 数据线索（前几个含 课程/节 字样的片段）===")
for mm in list(re.finditer(r"(大学英语|高等数学|结构力学|智能测绘|线性代数)", page))[:3]:
    i = mm.start()
    print("  ...", page[max(0, i-150):i+150].replace("\n", " ")[:300], "\n")
