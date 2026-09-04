#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
新余学院门户 SSO 链路探测脚本（M1 · v3）
v3 核心机制（逆向自 third chunk openThirdPage）:
    第三方应用打开方式 = 应用 href + "?ticket=<门户token>"（hrefType!=3/4/5）
    应用列表接口 = GET /api/v4/api/thirdService/getThirdSystem
"""
import sys
import time
import random
import argparse
import json

import requests
from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad

BASE = "https://ehallmobile.xyc.edu.cn"
API = BASE + "/api/v4/api"
JWXT = "https://zfjwxt.xyc.edu.cn"
MASTER_KEY = b"dc651e062a92599aa1230153"
ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


def log(*a):
    print(*a, flush=True)


def random_key24() -> bytes:
    return "".join(random.choice(ALNUM) for _ in range(24)).encode("utf-8")


def des3_ecb_hex(data: bytes, key: bytes) -> str:
    cipher = DES3.new(key, DES3.MODE_ECB)
    return cipher.encrypt(pad(data, 8)).hex()


def re_encrypt(account: str, password: str) -> dict:
    rk = random_key24()
    return {
        "userName": des3_ecb_hex(account.encode("utf-8"), rk),
        "userPassWord": des3_ecb_hex(password.encode("utf-8"), rk),
        "keys": des3_ecb_hex(rk, MASTER_KEY),
    }


def show_redirects(resp):
    for h in resp.history:
        log(f"    {h.status_code} -> {h.headers.get('Location', '')[:160]}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("account")
    ap.add_argument("password")
    args = ap.parse_args()

    s = requests.Session()
    s.headers.update({"User-Agent": UA, "Referer": BASE + "/mobile/index", "Origin": BASE})

    # ---------- [1] 登录 ----------
    enc = re_encrypt(args.account, args.password)
    payload = {
        "userDevice": args.account + str(int(time.time() * 1000)),
        "loginName": enc["userName"],
        "key": enc["keys"],
        "passWord": enc["userPassWord"],
        "loginType": "2",
    }
    log("[1] 登录门户 ...")
    j = s.post(API + "/login", data=payload, timeout=30).json()
    if str(j.get("code")) != "200":
        log(">> 登录失败:", j.get("code"), j.get("message"))
        sys.exit(2)
    token = j["token"]
    auth = {"Authorization": token}
    user = (j.get("data") or {}).get("casUserInfo") or {}
    office = ((user.get("office") or {}).get("name")) if isinstance(user.get("office"), dict) else ""
    log(">> 登录成功 | token:", token[:20], "... | 姓名:", user.get("name"), "| 单位:", office)

    # ---------- [2] 第三方应用列表 ----------
    log("\n[2] 获取第三方应用列表 /app/getApplication")
    apps = []
    for method in ("GET", "POST"):
        try:
            if method == "GET":
                r2 = s.get(API + "/app/getApplication", headers=auth, timeout=30)
            else:
                r2 = s.post(API + "/app/getApplication", headers=auth, timeout=30)
            jd = r2.json()
            data = jd.get("data")
            if isinstance(data, list) and data:
                log(f"HTTP {r2.status_code}（{method}）→ {len(data)} 个应用")
                apps = data
                break
            log(f"HTTP {r2.status_code}（{method}）→ code={jd.get('code')} data 预览:",
                json.dumps(data, ensure_ascii=False)[:300])
        except Exception as e:
            log(f"{method} 异常:", e)
    log(f"共 {len(apps)} 个应用：")
    target = None
    for a in apps:
        if not isinstance(a, dict):
            continue
        name = a.get("name") or a.get("thirdApplicationName") or "?"
        href = a.get("href") or a.get("url") or ""
        htype = a.get("hrefType")
        log(f"    - {name:16s} hrefType={htype}  href={href[:90]}")
        if any(k in (href + name).lower() for k in ["zfjwxt", "jwglxt", "教务"]):
            target = a

    # ---------- [3] SSO 进教务 ----------
    if not target:
        log("\n>> 列表中未发现教务应用！尝试常见教务入口 + ticket")
        candidates = [
            JWXT + "/jwglxt/xtgl/index_initMenu.html",
            JWXT + "/jwglxt/xtgl/login_sso.html",
        ]
    else:
        href = (target.get("href") or target.get("url") or "").strip()
        log(f"\n[3] 教务应用: {target.get('name') or target.get('thirdApplicationName')} hrefType={target.get('hrefType')}")
        sep = "&" if "?" in href else "?"
        candidates = [f"{href}{sep}ticket={token}"]

    for url in candidates:
        if url.startswith("/"):
            url = BASE + url
        log("GET", url[:130])
        try:
            r3 = s.get(url, headers=auth, allow_redirects=True, timeout=30)
        except Exception as e:
            log("   异常:", e)
            continue
        show_redirects(r3)
        log("   最终:", r3.url[:130], "| HTTP", r3.status_code, "| 长度:", len(r3.text))
        if "zfjwxt" in r3.url and r3.status_code == 200 and "login" not in r3.url.lower():
            log("   >> 该入口成功进入教务！")

    # ---------- [4] 教务会话验证 ----------
    log("\n[4] 教务会话验证: GET index_initMenu.html")
    r4 = s.get(JWXT + "/jwglxt/xtgl/index_initMenu.html?jsdm=xs", headers=auth,
               allow_redirects=True, timeout=30)
    show_redirects(r4)
    ok = (r4.status_code == 200 and "zfjwxt" in r4.url
          and "login" not in r4.url.lower() and len(r4.text) > 5000)
    log("最终:", r4.url[:120], "| HTTP", r4.status_code, "| 长度:", len(r4.text))
    log(">> " + ("教务会话有效，SSO 完全打通！" if ok else "尚未打通，需分析上面链路"))

    log("\n教务域 Cookie:")
    for c in s.cookies:
        if "zfjwxt" in (c.domain or ""):
            log(f"    {c.name:14s} {str(c.value)[:24]}")
    if ok:
        with open("D:/workdoc/app/campus-app/docs/probe/jwxt_index.html", "w", encoding="utf-8") as f:
            f.write(r4.text)
        log("（教务首页 HTML 已存 docs/probe/jwxt_index.html 供分析菜单/接口）")


if __name__ == "__main__":
    main()
