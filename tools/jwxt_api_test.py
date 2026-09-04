#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
教务业务接口实测（M1 终章）：登录 → SSO → 试探课表/成绩/菜单端点
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


def random_key24():
    return "".join(random.choice(ALNUM) for _ in range(24)).encode("utf-8")


def des3_ecb_hex(data: bytes, key: bytes) -> str:
    return DES3.new(key, DES3.MODE_ECB).encrypt(pad(data, 8)).hex()


def login_and_sso(account, password):
    s = requests.Session()
    s.headers.update({"User-Agent": UA, "Referer": BASE + "/mobile/index", "Origin": BASE})
    rk = random_key24()
    payload = {
        "userDevice": account + str(int(time.time() * 1000)),
        "loginName": des3_ecb_hex(account.encode(), rk),
        "key": des3_ecb_hex(rk, MASTER_KEY),
        "passWord": des3_ecb_hex(password.encode(), rk),
        "loginType": "2",
    }
    j = s.post(API + "/login", data=payload, timeout=30).json()
    assert str(j.get("code")) == "200", f"登录失败: {j.get('message')}"
    token = j["token"]
    s.headers["Authorization"] = token
    log("[1] 门户登录 OK，token:", token[:18], "...")
    r = s.get(JWXT + "/sso/xyoauthlogin?ticket=" + token, allow_redirects=True, timeout=30)
    log("[2] 教务 SSO →", r.url[:90], "HTTP", r.status_code)
    assert "login" not in r.url.lower(), "SSO 未打通: " + r.url
    return s


def try_endpoint(s, tag, method, url, form=None):
    try:
        if method == "POST":
            r = s.post(url, data=form or {}, headers={
                "X-Requested-With": "XMLHttpRequest",
                "Referer": JWXT + "/jwglxt/xtgl/index_initMenu.html",
            }, timeout=25)
        else:
            r = s.get(url, headers={"X-Requested-With": "XMLHttpRequest"}, timeout=25)
        body = r.text
        is_json = body.strip().startswith("{") or body.strip().startswith("[")
        log(f"[{tag}] {method} {url.split('xyc.edu.cn')[1][:80]}")
        log(f"     HTTP {r.status_code} JSON={is_json} len={len(body)}")
        if is_json and r.status_code == 200:
            log("     " + body[:600].replace("\n", " "))
            return body
        elif r.status_code != 200:
            log("     跳转:", r.url[-60:], "前100字:", body[:100].replace("\n", " "))
        return None
    except Exception as e:
        log(f"[{tag}] 异常:", e)
        return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("account")
    ap.add_argument("password")
    args = ap.parse_args()

    s = login_and_sso(args.account, args.password)

    # ---------- 菜单（找真实 gnmdm） ----------
    log("\n===== 菜单探测 =====")
    menu = try_endpoint(s, "菜单A", "GET", JWXT + "/jwglxt/xtgl/menu_cxXsKcdg.html?gnmkdm=N310001")
    menu = menu or try_endpoint(s, "菜单B", "POST", JWXT + "/jwglxt/xtgl/menu_cxMenuStudent.html?gnmkdm=N310001")
    if menu:
        try:
            jm = json.loads(menu)
            def walk(o, path=""):
                if isinstance(o, dict):
                    nm = o.get("menuName") or o.get("name") or ""
                    code = o.get("menuCode") or o.get("gnmkdm") or o.get("url") or ""
                    if nm and any(k in nm for k in ["课表", "成绩", "考试", "个人信息", "学籍"]):
                        log(f"    ★ {nm:12s} {code}")
                    for v in o.values():
                        walk(v)
                elif isinstance(o, list):
                    for v in o:
                        walk(v)
            walk(jm)
        except Exception as e:
            log("菜单解析:", e)

    # ---------- 课表 ----------
    log("\n===== 课表端点探测 =====")
    kb_forms = [("2026", "3"), ("2025", "12"), ("2025", "3")]
    for endp in [
        "/jwglxt/kbdy/bczd_cxXskbdyIndex.html?gnmkdm=N2151",
        "/jwglxt/kbdy/bczd_cxXskbdy.html?gnmkdm=N2151",
        "/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151",
        "/jwglxt/xskb/xskb_list.do?gnmkdm=N2151",
    ]:
        for xnm, xqm in kb_forms[:1]:
            body = try_endpoint(s, "课表", "POST", JWXT + endp,
                                {"xnm": xnm, "xqm": xqm, "gnmkdm": "N2151"})
            if body and ("kbList" in body or "xskb" in body):
                log("    ★★ 课表端点命中:", endp)
                break

    # ---------- 成绩 ----------
    log("\n===== 成绩端点探测 =====")
    for endp in [
        "/jwglxt/cjcx/cjcx_cxXsKccjList.html?gnmkdm=N305005",
        "/jwglxt/cjcx/cjcx_cxXscjList.html?gnmkdm=N305005",
    ]:
        for xnm, xqm in [("2025", "3"), ("2025", "12")]:
            body = try_endpoint(s, "成绩", "POST", JWXT + endp, {
                "xnm": xnm, "xqm": xqm, "kslb": "", "kch": "", "kcmc": "",
                "xsfs": "all", "ysfxd": "0",
            })
            if body and ("jcList" in body or "kcmc" in body):
                log(f"    ★★ 成绩端点命中: {endp} (xnm={xnm}, xqm={xqm})")
                break


if __name__ == "__main__":
    main()
