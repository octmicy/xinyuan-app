# -*- coding: utf-8 -*-
"""v9 终章：完整成绩链路 SSO→wapLogin→pkey→数据，保存样本"""
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
print("[SSO]", "OK" if "login" not in r.url.lower() else "FAIL")
html = r.text

# 取成绩菜单的 wapLogin 签名参数
m = re.search(
    r"clickMenu\('([^']*)','([^']*)','(Y305005)','([^']*)','([^']*)','([^']*)','([^']*)'\)"
    r'[^>]*title="([^"]*)"',
    html)
assert m, "未找到成绩菜单"
procode, typ, choice, uid, role, key, ts, title = m.groups()
print(f"[成绩菜单] choice={choice} title={title}")

# wapLogin → 成绩页面
wap = (JWXT + f"/jwglxt/xtgl/login_wapLogin.html?procode={procode}&type={typ}"
       f"&choice={choice}&uid={uid}&role={role}&key={key}&time={ts}")
r2 = s.get(wap, allow_redirects=True, timeout=25)
page = r2.text
print("[成绩页面]", r2.url[:90], "HTTP", r2.status_code, "len", len(page))

pkey_m = re.search(r'id="pkey"[^>]*value="([^"]*)"', page) or re.search(
    r'name="pkey"[^>]*value="([^"]*)"', page)
pkey = pkey_m.group(1) if pkey_m else ""
print("[pkey]", pkey[:40], "...")

# 学期选项
xnms = re.findall(r'<option[^>]*value="(\d{4})"[^>]*>', page)
print("[可选学年]", sorted(set(xnms)))
xqms = re.findall(r'<option[^>]*value="(\d+)"[^>]*>\s*(第?[一二三]学期|\d+)', page)
print("[可选学期]", sorted(set(xqms))[:6])

# POST 成绩（pkey 页面初始为空，直接随表单发送）
hdr = {"X-Requested-With": "XMLHttpRequest", "Referer": r2.url}
results = {}
for xnm, xqm in [("2025", "3"), ("2025", "12"), ("2026", "3")]:
    r3 = s.post(JWXT + "/jwglxt/cjcx/cjcxMobile_cxXsgrcj.html?doType=app",
                data={"xnm": xnm, "xqm": xqm, "pkey": pkey}, headers=hdr, timeout=25)
    print(f"[成绩 {xnm}-{xqm}] HTTP {r3.status_code} len={len(r3.text)}",
          "预览:", r3.text[:150].replace("\n", " "))
    if r3.status_code == 200 and r3.text.strip().startswith("["):
        arr = r3.json()
        results[f"{xnm}-{xqm}"] = arr
        if arr:
            print(f"    ★ 成绩 {len(arr)} 条！首条:", json.dumps(arr[0], ensure_ascii=False)[:500])
if results:
    with open("D:/workdoc/app/campus-app/docs/probe/cj_mobile.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=1)
    print("已存 cj_mobile.json")
