# -*- coding: utf-8 -*-
"""用户裁剪的 7 张图标处理：紧凑裁白边 → 缩放 → 替换 App 资源"""
import numpy as np
from PIL import Image

D = r"D:\workdoc\app"
OUT = r"D:\workdoc\app\campus-app\app\src\main\res\drawable-nodpi"
G = r"D:\workdoc\app\campus-app\app\src\main\res\drawable-xxxhdpi"


def tight(im: Image.Image) -> Image.Image:
    a = np.array(im.convert("RGBA"))
    bg = (a[:, :, :3] >= 250).all(-1)
    ys, xs = np.where(~bg)
    return im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def fit(im: Image.Image, size: int, content: int) -> Image.Image:
    s = min(content / im.width, content / im.height)
    im2 = im.convert("RGBA").resize(
        (max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS
    )
    c = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c.alpha_composite(im2, ((size - im2.width) // 2, (size - im2.height) // 2))
    return c


pairs = {
    "2.png": "nav_schedule",
    "3.png": "nav_grades",
    "4.png": "nav_apps",
    "5.png": "nav_leave",
    "7.png": "nav_profile",
}
for src, name in pairs.items():
    t = tight(Image.open(D + "\\" + src))
    fit(t, 256, 244).save(OUT + "\\" + name + ".png")
    print(name, "OK", t.size)

t1 = tight(Image.open(D + "\\" + "1.png"))
fit(t1, 432, 400).save(G + "\\" + "ic_launcher_foreground.png")
print("launcher foreground OK", t1.size)
