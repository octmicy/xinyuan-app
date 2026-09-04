#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
教务业务接口实测 v5：课表(历史学期) + 成绩(queryModel 补全)，保存 JSON 样本
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
OUT = "D:/workdoc/app/campus-app/docs/probe"


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
    log("[1] 门户登录 OK")
    r = s.get(JWXT + "/sso/xyoauthlogin?ticket=" + token, allow_redirects=True, timeout=30)
    assert "login" not in r.url.lower(), "SSO 失败"
    log("[2] 教务 SSO OK")
    return s


def post_json(s, url, form, tag=""):
    r = s.post(url, data=form, headers={
        "X-Requested-With": "XMLHttpRequest",
        "Referer": JWXT + "/jwglxt/xtgl/index_initMenu.html",
    }, timeout=25)
    body = r.text
    ok = r.status_code == 200 and body.strip().startswith("{")
    log(f"[{tag}] HTTP {r.status_code} len={len(body)} json={ok}")
    return (json.loads(body) if ok else None)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("account")
    ap.add_argument("password")
    args = ap.parse_args()

    s = login_and_sso(args.account, args.password)
    hdr = {"X-Requested-With": "XMLHttpRequest",
           "Referer": JWXT + "/jwglxt/xtgl/index_initMenu.html"}

    # ---------- 课表：历史学期 ----------
    log("\n===== 课表（kbcx/xskbcx_cxXsKb，多学期） =====")
    for xnm, xqm in [("2025", "3"), ("2025", "12"), ("2026", "3")]:
        j = post_json(s, JWXT + "/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151",
                      {"xnm": xnm, "xqm": xqm}, f"课表{xnm}-{xqm}")
        if j is not None:
            kb = j.get("kbList") or []
            log(f"    kbList 条数 = {len(kb)}")
            if kb:
                log("    第一条:", json.dumps(kb[0], ensure_ascii=False)[:500])
                fn = f"{OUT}/kb_{xnm}_{xqm}.json"
                with open(fn, "w", encoding="utf-8") as f:
                    json.dump(j, f, ensure_ascii=False, indent=1)
                log("    已存", fn)

    # ---------- 成绩：queryModel 补全 ----------
    log("\n===== 成绩（cjcx_cxXsKccjList + queryModel） =====")
    for xnm, xqm in [("2025", "3"), ("2025", "12")]:
        form = {
            "xnm": xnm, "xqm": xqm,
            "kslb": "", "kch": "", "kcmc": "", "xsfs": "all", "ysfxd": "0",
            "queryModel.showCount": "1000",
            "queryModel.currentPage": "1",
            "queryModel.pageName": "",
            "queryModel.sorts": "",
            "timeFlag": "false",
        }
        j = post_json(s, JWXT + "/jwglxt/cjcx/cjcx_cxXsKccjList.html?gnmkdm=N305005",
                      form, f"成绩{xnm}-{xqm}")
        if j is not None:
            items = j.get("items") or []
            log(f"    items 条数 = {len(items)}")
            if items:
                log("    第一条:", json.dumps(items[0], ensure_ascii=False)[:600])
                fn = f"{OUT}/cj_{xnm}_{xqm}.json"
                with open(fn, "w", encoding="utf-8") as f:
                    json.dump(j, f, ensure_ascii=False, indent=1)
                log("    已存", fn)
                break

    # ---------- 成绩全部学年（xnm/xqm 留空试探） ----------
    log("\n===== 成绩（全学年试探） =====")
    form = {
        "xnm": "", "xqm": "",
        "kslb": "", "kch": "", "kcmc": "", "xsfs": "all", "ysfxd": "0",
        "queryModel.showCount": "1000",
        "queryModel.currentPage": "1",
        "queryModel.pageName": "",
        "queryModel.sorts": "",
        "timeFlag": "false",
    }
    j = post_json(s, JWXT + "/jwglxt/cjcx/cjcx_cxXsKccjList.html?gnmkdm=N305005",
                  form, "成绩全部")
    if j is not None:
        items = j.get("items") or []
        log("    items 条数 =", len(items))
        if items:
            terms = sorted({(i.get("xnm"), i.get("xqm")) for i in items})
            log("    涉及学期:", terms)
            fn = f"{OUT}/cj_all.json"
            with open(fn, "w", encoding="utf-8") as f:
                json.dump(j, f, ensure_ascii=False, indent=1)
            log("    已存", fn)


if __name__ == "__main__":
    main()
