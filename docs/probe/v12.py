# -*- coding: utf-8 -*-
"""v12: 仅登录，打印完整响应（诊断风控状态）"""
import sys, time, random, json
import requests
from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad

BASE = "https://ehallmobile.xyc.edu.cn"
API = BASE + "/api/v4/api"
MASTER_KEY = b"dc651e062a92599aa1230153"
ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
account, password = sys.argv[1], sys.argv[2]
device = sys.argv[3] if len(sys.argv) > 3 else account + str(int(time.time() * 1000))

rk = "".join(random.choice(ALNUM) for _ in range(24)).encode("utf-8")
s = requests.Session()
s.headers.update({"User-Agent": UA, "Referer": BASE + "/mobile/index", "Origin": BASE})
payload = {
    "userDevice": device,
    "loginName": DES3.new(rk, DES3.MODE_ECB).encrypt(pad(account.encode(), 8)).hex(),
    "key": DES3.new(MASTER_KEY, DES3.MODE_ECB).encrypt(pad(rk, 8)).hex(),
    "passWord": DES3.new(rk, DES3.MODE_ECB).encrypt(pad(password.encode(), 8)).hex(),
    "loginType": "2",
}
r = s.post(API + "/login", data=payload, timeout=30)
print("HTTP", r.status_code)
print("userDevice:", device)
print("响应原文:", r.text[:800])
