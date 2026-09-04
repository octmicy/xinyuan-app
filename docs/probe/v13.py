# -*- coding: utf-8 -*-
"""v13: 探测考试安排 Y357005 的落地页与数据接口"""
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
r = s.get(JWXT + "/sso/xyoauthlogin?ticket=" + token, allow_redirects=True, timeout=30)
html = r.text
print("[SSO]", "OK" if "login" not in r.url.lower() else "FAIL")

# 考试菜单（Y357005）
m = re.search(
    r"clickMenu\('([^']*)','([^']*)','(Y357005)','([^']*)','([^']*)','([^']*)','([^']*)'\)"
    r'[^>]*title="([^"]*)"',
    html)
if not m:
    print("未找到 Y357005")
    sys.exit(1)
procode, typ, choice, uid, role, key, ts, title = m.groups()
print("[菜单]", title, choice)

wap = (JWXT + f"/jwglxt/xtgl/login_wapLogin.html?procode={procode}&type={typ}"
       f"&choice={choice}&uid={uid}&role={role}&key={key}&time={ts}")
r2 = s.get(wap, allow_redirects=True, timeout=30)
page = r2.text
print("[落地页]", r2.url[:110], "| HTTP", r2.status_code, "| len", len(page))
with open("D:/workdoc/app/campus-app/docs/probe/exam_page.html", "w", encoding="utf-8") as f:
    f.write(page)

print("\n=== 页面接口 ===")
for u in sorted(set(re.findall(r'["\'](/[A-Za-z][A-Za-z0-9_/]+\.html[^"\']*)["\']', page)))[:12]:
    print("  ", u[:100])
print("\n=== 页面里的隐藏参数/字段 ===")
for m2 in re.finditer(r'<input[^>]*type="hidden"[^>]*>', page):
    print("  ", m2.group(0)[:160])
print("\n=== ajax 调用 ===")
for mm in list(re.finditer(r"\$\.post\(([^;]{0,150})", page))[:6]:
    print("  ", mm.group(0)[:170].replace(chr(10), " "))

# 请求数据
hdr = {"X-Requested-With": "XMLHttpRequest", "Referer": r2.url}
r3 = s.post(JWXT + "/jwglxt/pkmdgl/ksmdglMobile_cxKsxxList.html?doType=app",
            data={"xnm": "2026", "xqm": "3"}, headers=hdr, timeout=25)
print("\n[考试数据] HTTP", r3.status_code, "len", len(r3.text))
print("原文:", r3.text[:900])
try:
    arr = r3.json()
    if isinstance(arr, list) and arr:
        print("条数:", len(arr), "| 首条字段:", json.dumps(arr[0], ensure_ascii=False)[:600])
        with open("D:/workdoc/app/campus-app/docs/probe/exam_data.json", "w", encoding="utf-8") as f:
            json.dump(arr, f, ensure_ascii=False, indent=1)
        print("已存 exam_data.json")
except Exception as e:
    print("解析:", e)
