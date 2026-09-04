# -*- coding: utf-8 -*-
"""图标集裁切：从用户生成的集合图中裁出各图标，去白底（边缘泛洪）、转透明、缩放输出"""
import os
from collections import deque
import numpy as np
from PIL import Image

SRC = r"D:\workdoc\app\01a06c73-5452-7403-a329-27928a0caffe.png"
OUT = r"D:\workdoc\app\campus-app\docs\icon-gen\cropped"
os.makedirs(OUT, exist_ok=True)

# 6 个 ROI（1024×1024 上目测的宽松包围框，泛洪去背景后会按 alpha 收紧）
ROIS = {
    "launcher": (250, 0, 760, 480),
    "schedule": (20, 460, 340, 750),
    "grades": (345, 460, 655, 750),
    "apps": (665, 460, 975, 750),
    "leave": (20, 740, 340, 1024),
    "profile": (665, 740, 975, 1024),
}


def remove_bg(im: Image.Image) -> Image.Image:
    """从边缘泛洪清除近白色背景（保留图标内部白色区域）"""
    im = im.convert("RGBA")
    a = np.array(im)
    h, w = a.shape[:2]
    near_white = (a[:, :, 0] >= 236) & (a[:, :, 1] >= 236) & (a[:, :, 2] >= 236)
    mask = np.zeros((h, w), dtype=bool)
    dq = deque()

    def seed(y, x):
        if near_white[y, x] and not mask[y, x]:
            mask[y, x] = True
            dq.append((y, x))

    for x in range(w):
        seed(0, x)
        seed(h - 1, x)
    for y in range(h):
        seed(y, 0)
        seed(y, w - 1)

    while dq:
        y, x = dq.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and near_white[ny, nx] and not mask[ny, nx]:
                mask[ny, nx] = True
                dq.append((ny, nx))

    a[mask, 3] = 0
    return Image.fromarray(a)


def tight_crop(im: Image.Image, pad_ratio: float = 0.04) -> Image.Image:
    arr = np.array(im)
    ys, xs = np.where(arr[:, :, 3] > 0)
    if len(ys) == 0:
        return im
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    pw = int((x1 - x0) * pad_ratio)
    ph = int((y1 - y0) * pad_ratio)
    x0 = max(0, x0 - pw)
    y0 = max(0, y0 - ph)
    x1 = min(im.width, x1 + pw)
    y1 = min(im.height, y1 + ph)
    return im.crop((x0, y0, x1, y1))


def fit_canvas(im: Image.Image, size: int, content: int) -> Image.Image:
    """把内容缩放至 content 像素内，居中贴到 size×size 透明画布"""
    scale = min(content / im.width, content / im.height)
    nw, nh = max(1, round(im.width * scale)), max(1, round(im.height * scale))
    im2 = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(im2, ((size - nw) // 2, (size - nh) // 2), im2)
    return canvas


src = Image.open(SRC)
results = {}
for name, box in ROIS.items():
    roi = src.crop(box)
    clean = remove_bg(roi)
    tight = tight_crop(clean)
    results[name] = tight
    out512 = fit_canvas(tight, 512, 512)
    out512.save(os.path.join(OUT, f"{name}_512.png"))
    print(f"{name}: tight {tight.size} -> 512x512 OK")

# launcher 自适应前景：108dp 中安全区 66dp → 432 画布内容约 300px
fg = fit_canvas(results["launcher"], 432, 300)
fg.save(os.path.join(OUT, "ic_launcher_foreground.png"))
print("launcher foreground 432 OK")

# 导航图标 256×256
for n in ("schedule", "grades", "apps", "leave", "profile"):
    fit_canvas(results[n], 256, 256).save(os.path.join(OUT, f"nav_{n}.png"))
print("nav icons 256 OK")
