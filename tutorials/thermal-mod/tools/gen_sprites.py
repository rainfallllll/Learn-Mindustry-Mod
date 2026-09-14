#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""热力学 mod 像素贴图生成器
按 Mindustry 官方像素风（32x32，1px 深色描边 + 色块嵌套 + 中心对称）绘制 4 张建筑贴图。
"""
from PIL import Image

SIZE = 32

def px(im, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        im.putpixel((x, y), color)

def rect(im, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, color)

def frame(im, color):
    """1px 深色描边"""
    rect(im, 0, 0, SIZE - 1, 0, color)
    rect(im, 0, SIZE - 1, SIZE - 1, SIZE - 1, color)
    rect(im, 0, 0, 0, SIZE - 1, color)
    rect(im, SIZE - 1, 0, SIZE - 1, SIZE - 1, color)

def shade(im, y0, y1, color):
    """横向高光/阴影带"""
    for y in range(y0, y1 + 1):
        for x in range(SIZE):
            c = im.getpixel((x, y))
            if c[3] > 0:
                im.putpixel((x, y), color)

# ---------- 工业锅炉 industrial-boiler ----------
def boiler():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    frame(im, (30, 30, 30, 255))                     # 外框
    rect(im, 2, 4, 29, 29, (70, 70, 72, 255))        # 铁灰机身
    rect(im, 10, 1, 21, 4, (55, 55, 58, 255))        # 顶部烟囱
    rect(im, 12, 2, 19, 4, (45, 45, 48, 255))        # 烟囱口
    rect(im, 5, 14, 26, 25, (92, 44, 38, 255))       # 炉门深红
    rect(im, 7, 16, 24, 23, (200, 88, 32, 255))      # 炉膛橙
    rect(im, 10, 18, 21, 21, (255, 168, 56, 255))    # 火焰亮心
    rect(im, 4, 8, 8, 12, (96, 96, 100, 255))        # 左侧铆钉柱
    rect(im, 23, 8, 27, 12, (96, 96, 100, 255))      # 右侧铆钉柱
    shade(im, 5, 6, (110, 110, 114, 255))            # 顶部高光
    return im

# ---------- 导热管 heat-conduit ----------
def conduit():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    frame(im, (40, 24, 12, 255))                     # 深铜外框
    rect(im, 2, 6, 29, 25, (150, 94, 44, 255))       # 铜管主体
    rect(im, 2, 6, 3, 25, (110, 68, 32, 255))        # 左法兰
    rect(im, 28, 6, 29, 25, (110, 68, 32, 255))      # 右法兰
    rect(im, 8, 4, 23, 6, (90, 55, 26, 255))         # 顶部接口
    rect(im, 10, 11, 21, 20, (230, 148, 66, 255))    # 管芯高光
    rect(im, 13, 14, 18, 17, (255, 190, 92, 255))    # 导热亮心
    rect(im, 6, 20, 9, 23, (255, 214, 140, 255))     # 温度指示点
    rect(im, 22, 20, 25, 23, (255, 214, 140, 255))   # 温度指示点
    shade(im, 7, 8, (178, 116, 58, 255))             # 顶部高光
    return im

# ---------- 精炼炉 refinery-furnace ----------
def furnace():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    frame(im, (28, 28, 30, 255))                     # 深灰外框
    rect(im, 2, 3, 29, 29, (108, 64, 44, 255))       # 耐火砖主体
    rect(im, 2, 3, 29, 5, (86, 50, 34, 255))         # 顶部砖带
    rect(im, 4, 10, 27, 26, (46, 40, 38, 255))       # 炉腔
    rect(im, 6, 12, 25, 24, (210, 96, 28, 255))      # 熔池橙
    rect(im, 9, 15, 22, 21, (255, 168, 60, 255))     # 熔池亮
    rect(im, 12, 17, 19, 19, (255, 224, 128, 255))   # 熔芯
    rect(im, 8, 7, 11, 9, (150, 150, 155, 255))      # 出料口
    rect(im, 20, 7, 23, 9, (150, 150, 155, 255))     # 出料口
    rect(im, 2, 12, 3, 20, (78, 44, 30, 255))        # 侧烟道
    rect(im, 28, 12, 29, 20, (78, 44, 30, 255))      # 侧烟道
    shade(im, 4, 4, (128, 80, 56, 255))              # 顶高光
    return im

# ---------- 散热塔 cooling-tower ----------
def tower():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    frame(im, (34, 52, 78, 255))                     # 深蓝外框
    rect(im, 4, 3, 27, 28, (138, 164, 184, 255))     # 塔身浅蓝
    rect(im, 9, 1, 22, 3, (118, 142, 162, 255))      # 顶部风筒
    rect(im, 11, 7, 20, 15, (74, 106, 138, 255))     # 散热格栅
    rect(im, 13, 9, 18, 13, (52, 80, 110, 255))      # 格栅芯
    rect(im, 7, 20, 24, 23, (96, 124, 148, 255))     # 中部环带
    rect(im, 5, 26, 26, 28, (70, 96, 122, 255))      # 底部基座
    rect(im, 3, 13, 4, 16, (180, 208, 228, 255))     # 左水雾
    rect(im, 27, 13, 28, 16, (180, 208, 228, 255))   # 右水雾
    rect(im, 12, 24, 19, 25, (150, 180, 200, 255))   # 排水口
    shade(im, 4, 4, (160, 186, 206, 255))            # 顶高光
    return im

sprites = {
    "industrial-boiler": boiler,
    "heat-conduit": conduit,
    "refinery-furnace": furnace,
    "cooling-tower": tower,
}

outdir = "/home/user/Doubao/chats/38441189011769346/thermal-mod/assets/sprites"
for name, fn in sprites.items():
    im = fn()
    im.save(f"{outdir}/{name}.png")
    print(f"✓ {name}.png {im.size}")

print("全部 4 张贴图已生成 →", outdir)
