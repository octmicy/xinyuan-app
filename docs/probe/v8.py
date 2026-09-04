# -*- coding: utf-8 -*-
"""v8: 提取教务首页内联菜单全表（clickMenu 参数）+ 用 Y 功能码查成绩"""
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

# ---------- 提取内联菜单 ----------
html = r.text
menus = re.findall(
    r"clickMenu\('([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)'\)"
    r'[^>]*title="([^"]*)"',
    html)
print(f"\n[菜单全表] 共 {len(menus)} 项：")
score_menu = exam_menu = None
for m in menus:
    procode, typ, choice, uid, role, key, ts, title = m
    print(f"    procode={procode} choice={choice:10s} title={title}")
    if "成绩" in title and score_menu is None:
        score_menu = m
    if "考试" in title and exam_menu is None:
        exam_menu = m

hdr = {"X-Requested-With": "XMLHttpRequest", "Referer": JWXT + "/jwglxt/xtgl/index_initMenu.html"}

# ---------- 用 Y 码查成绩 ----------
if score_menu:
    procode, typ, choice, uid, role, key, ts, title = score_menu
    print(f"\n[成绩查询] 功能码 {choice}")
    for gnm in [choice, choice.replace("Y", "N", 1)]:
        form = {
            "xnm": "2025", "xqm": "3",
            "kslb": "", "kch": "", "kcmc": "", "xsfs": "all", "ysfxd": "0",
            "queryModel.showCount": "1000",
            "queryModel.currentPage": "1",
            "queryModel.pageName": "",
            "queryModel.sorts": "",
            "timeFlag": "false",
        }
        r2 = s.post(JWXT + f"/jwglxt/cjcx/cjcx_cxXsKccjList.html?gnmkdm={gnm}",
                    data=form, headers=hdr, timeout=25)
        print(f"  gnmkdm={gnm}: HTTP {r2.status_code} len={len(r2.text)}",
              "预览:", r2.text[:200].replace("\n", " "))
        if r2.status_code == 200 and r2.text.strip().startswith("{"):
            j = r2.json()
            items = j.get("items") or []
            print(f"  ★★ 成绩 {len(items)} 条！")
            if items:
                print("  第一条:", json.dumps(items[0], ensure_ascii=False)[:600])
                with open(f"D:/workdoc/app/campus-app/docs/probe/cj_{gnm}.json", "w", encoding="utf-8") as f:
                    json.dump(j, f, ensure_ascii=False, indent=1)
            break

# ---------- wapLogin 跳转链（成绩页面入口） ----------
if score_menu:
    procode, typ, choice, uid, role, key, ts, title = score_menu
    wap = (JWXT + f"/jwglxt/xtgl/login_wapLogin.html?procode={procode}&type={typ}"
           f"&choice={choice}&uid={uid}&role={role}&key={key}&time={ts}")
    print("\n[wapLogin 跳转]", wap[:130])
    r3 = s.get(wap, allow_redirects=True, headers=hdr, timeout=25)
    for h in r3.history:
        print("   ", h.status_code, "->", h.headers.get("Location", "")[:120])
    print("   最终:", r3.url[:130], "HTTP", r3.status_code, "len", len(r3.text))
    # 落地页面里找真实的数据接口
    if r3.status_code == 200 and len(r3.text) > 3000:
        page = r3.text
        urls = sorted(set(re.findall(r'["\'](/[A-Za-z]+/[A-Za-z0-9_/]+\.html)[^"\']*', page)))
        print("   页面端点:", urls[:15])
        gns = sorted(set(re.findall(r"gnmkdm=([A-Z]\d+)", page)))
        print("   页面功能码:", gns[:15])
        with open("D:/workdoc/app/campus-app/docs/probe/cj_wap_page.html", "w", encoding="utf-8") as f:
            f.write(page)
        print("   (已存 cj_wap_page.html)")
