# -*- coding: utf-8 -*-
"""v7: 从教务首页 JS/菜单接口找该校真实的成绩功能码 gnmdm"""
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

hdr = {"X-Requested-With": "XMLHttpRequest", "Referer": JWXT + "/jwglxt/xtgl/index_initMenu.html"}
su_hdr = dict(hdr)

# 1. 首页 HTML 引用的 JS
idx = open("D:/workdoc/app/campus-app/docs/probe/jwxt_index.html", encoding="utf-8", errors="ignore").read()
js_list = re.findall(r'src="(/jwglxt/[^"]+\.js[^"]*)"', idx)
print("首页引用 JS:", *["  " + u[:90] for u in js_list], sep="\n")

# 2. 尝试多个菜单端点
candidates = [
    ("GET",  "/jwglxt/xtgl/menu_cxMenu.html?gnmkdm=N310001"),
    ("POST", "/jwglxt/xtgl/menu_cxXsKcdg.html?gnmkdm=N310001"),
    ("GET",  "/jwglxt/xtgl/menu_cxXsKcdg.html?gnmkdm=N310001&su=" + account),
    ("POST", "/jwglxt/xtgl/menu_cxMenuStudent.html?gnmkdm=N310001&su=" + account),
    ("GET",  "/jwglxt/xtgl/menu_cxYhcd.html?gnmkdm=N310001"),
    ("POST", "/jwglxt/f/menu_cxMenu.html?gnmkdm=N310001"),
]
for method, path in candidates:
    url = JWXT + path
    try:
        r2 = (s.post(url, headers=hdr, timeout=20) if method == "POST"
              else s.get(url, headers=hdr, timeout=20))
        body = r2.text
        hit = r2.status_code == 200 and body.strip().startswith(("{", "["))
        print(f"{method} {path[:70]}  -> {r2.status_code} len={len(body)} json={hit}")
        if hit:
            print("  预览:", body[:300].replace("\n", " "))
            with open("D:/workdoc/app/campus-app/docs/probe/menu.json", "w", encoding="utf-8") as f:
                f.write(body)
            # 找成绩/考试/学籍相关功能
            def walk(o, found):
                if isinstance(o, dict):
                    nm = str(o.get("menuName") or o.get("name") or "")
                    code = str(o.get("menuCode") or o.get("url") or "")
                    if nm and any(k in nm for k in ["成绩", "考试", "学籍", "个人信息"]):
                        found.append((nm, code))
                    for v in o.values():
                        walk(v, found)
                elif isinstance(o, list):
                    for v in o:
                        walk(v, found)
            found = []
            walk(json.loads(body), found)
            for nm, code in found[:20]:
                print("    ★", nm, "->", code[:100])
            break
    except Exception as e:
        print(f"{method} {path[:60]} 异常:", e)
