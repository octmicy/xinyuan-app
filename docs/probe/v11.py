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
html = r.text
print("[SSO]", "OK" if "login" not in r.url.lower() else "FAIL")
m = re.search(r"clickMenu\('([^']*)','([^']*)','(Y253510)','([^']*)','([^']*)','([^']*)','([^']*)'\)", html)
procode, typ, choice, uid, role, key, ts = m.groups()
wap = (JWXT + f"/jwglxt/xtgl/login_wapLogin.html?procode={procode}&type={typ}&choice={choice}&uid={uid}&role={role}&key={key}&time={ts}")
r2 = s.get(wap, allow_redirects=True, timeout=30)
page_url = r2.url
print("[旧课表页]", page_url[:100])
hdr = {"X-Requested-With": "XMLHttpRequest", "Referer": page_url}
# 1. 周次列表
r3 = s.post(JWXT + "/jwglxt/kbcx/xskbcxMobile_cxZc.html", data={"xnm": "2026", "xqm": "3"}, headers=hdr, timeout=25)
print("[周次列表]", r3.status_code, r3.text[:300])
weeks = r3.json() if r3.text.strip().startswith("[") else []
# 2. 按周查课表
for zs in ["1", "2"]:
    r4 = s.post(JWXT + "/jwglxt/kbcx/xskbcxMobile_cxXsKb.html",
                data={"xnm": "2026", "xqm": "3", "zs": zs, "doType": "app", "kblx": ""},
                headers=hdr, timeout=25)
    print(f"\n[课表 zs={zs}] HTTP {r4.status_code} len={len(r4.text)}")
    if r4.text.strip().startswith("{"):
        j = r4.json()
        kb = j.get("kbList") or []
        print("  kbList 条数:", len(kb))
        for it in kb[:6]:
            print(f"   xqj={it.get('xqj')} jcor={it.get('jcor')} {it.get('kcmc')} | {it.get('zcd')} | {it.get('cdmc')}")
        if zs == "1" and kb:
            with open("D:/workdoc/app/campus-app/docs/probe/kb_old_z1.json", "w", encoding="utf-8") as f:
                json.dump(j, f, ensure_ascii=False, indent=1)
            print("  已存 kb_old_z1.json")
