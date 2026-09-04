# -*- coding: utf-8 -*-
"""8 宫格应用图标（用户裁剪 7 张）→ App 资源"""
import numpy as np
from PIL import Image

D = r"D:\workdoc\app\new"
OUT = r"D:\workdoc\app\campus-app\app\src\main\res\drawable-nodpi"

pairs = {
    "1.png": "app_jwxt",       # 教务系统
    "2.png": "app_library",    # 我的图书馆
    "3.png": "app_career",     # 就业系统
    "4.png": "app_graduate",   # 毕业生离校系统
    "5.png": "app_xg",         # 学工系统
    "6.png": "app_pay",        # 学生缴费
    "7.png": "app_online",     # 网络教学系统
}


def tight(im: Image.Image) -> Image.Image:
    a = np.array(im.convert("RGBA"))
    bg = (a[:, :, :3] >= 250).all(-1)
    ys, xs = np.where(~bg)
    return im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def fit256(im: Image.Image) -> Image.Image:
    s = min(248 / im.width, 248 / im.height)
    im2 = im.convert("RGBA").resize(
        (max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS
    )
    c = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    c.alpha_composite(im2, ((256 - im2.width) // 2, (256 - im2.height) // 2))
    return c


for src, name in pairs.items():
    t = tight(Image.open(D + "\\" + src))
    fit256(t).save(OUT + "\\" + name + ".png")
    print(name, "OK", t.size)
