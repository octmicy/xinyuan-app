# -*- coding: utf-8 -*-
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

# 1. 9 字节响应原文
r1 = s.post(JWXT + "/jwglxt/cjcx/cjcx_cxXsKccjList.html?gnmkdm=N305005",
            data={"xnm": "2025", "xqm": "3", "xsfs": "all", "ysfxd": "0",
                  "kslb": "", "kch": "", "kcmc": "",
                  "queryModel.showCount": "1000", "queryModel.currentPage": "1",
                  "queryModel.pageName": "", "queryModel.sorts": "", "timeFlag": "false"},
            headers=hdr, timeout=25)
print("[成绩KccjList 原文]", repr(r1.text))

# 2. GET 成绩查询页面 HTML，挖端点与 gnmdm
r2 = s.get(JWXT + "/jwglxt/cjcx/cjcx_cxXscjList.html?gnmkdm=N305005", headers=hdr, timeout=25)
html = r2.text
print("[成绩页面] HTTP", r2.status_code, "len", len(html))
print("页面中的 gnmkdm:", sorted(set(re.findall(r"gnmkdm=(N\d+)", html))))
print("页面中的 cjcx 方法:", sorted(set(re.findall(r"(cjcx_\w+\.html)", html)))[:10])
print("页面中的 ajax url:", sorted(set(re.findall(r"[\"'](/[A-Za-z]+/[A-Za-z0-9_/]+\.html)[^\"']*", html)))[:15])
with open("D:/workdoc/app/campus-app/docs/probe/cj_page.html", "w", encoding="utf-8") as f:
    f.write(html)
print("(页面已存 cj_page.html)")
