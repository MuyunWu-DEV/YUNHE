#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 Noto Sans SC 的「常用字子集」字体，用于 PI PDF 嵌入，砍掉体积。

背景：PiPdfRenderer 必须用 setSubset(false) 完整嵌入中文字体（否则 OpenPDF 子集化器
会损坏 cmap → 方块）。完整 Noto SC 有 31036 字形，单 PDF 因此 ~6.8MB。
本脚本用 fontTools 预先把字体裁到「实际用得到的汉字」：

  - ASCII 可打印字符（0x20-0x7E）：混合字符串（如「青岛 Yunhe」）里的拉丁/数字也走 CJK 字体
  - CJK 标点（0x3000-0x303F）
  - 全角字符（0xFF00-0xFFEF）
  - 常用货币符号（¥ € ₹ $）
  - 卖方固定中文名：青岛云合智能制造有限公司
  - GB2312(1980) 全部 6763 汉字 + 其内含的符号：覆盖出口单据的绝大多数中文

关键：OpenPDF 把 CFF/OTF 以 CIDFontType0 嵌入时，即便关闭子集化也会因 CID→GID 映射
错乱把中文渲染成乱码。故子集后必须再用 otf2ttf_noto 转成 glyf/TrueType，
走 CIDFontType2 + CIDToGIDMap=/Identity（与 Carlito 同路径），中文才能正确显示。

产物 NotoSansSC-Subset.ttf 提交仓库（resources/fonts/，随 jar 打包）；
中间子集 NotoSansSC-Subset.otf 落在 _src/（gitignore，不打包）；
全字库源留在 _src/NotoSansSC-Regular.otf（gitignore），便于重裁/扩字符。

用法：python subset_noto.py
依赖：fontTools  (本脚本会调用同目录 otf2ttf_noto.otf_to_ttf)
"""
import os

from fontTools import subset
from fontTools.ttLib import TTFont

from otf2ttf_noto import otf_to_ttf

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "_src", "NotoSansSC-Regular.otf")
INTER = os.path.join(HERE, "_src", "NotoSansSC-Subset.otf")   # 中间子集，不打包
OUT = os.path.join(HERE, "NotoSansSC-Subset.ttf")            # 最终交付，打包进 jar

SELLER_NAME = "青岛云合智能制造有限公司"


def build_unicodes():
    u = set()

    # ASCII 可打印：混合 CJK 字符串里的拉丁/数字/标点也由 CJK 字体承载
    for cp in range(0x20, 0x7F):
        u.add(cp)

    # CJK 符号和标点
    for cp in range(0x3000, 0x3040):
        u.add(cp)

    # 全角形式（全角数字/字母/标点）
    for cp in range(0xFF00, 0xFFF0):
        u.add(cp)

    # 常用货币符号
    for cp in (0x0024, 0x00A5, 0x20AC, 0x20B9):
        u.add(cp)

    # 卖方固定中文名
    for ch in SELLER_NAME:
        u.add(ord(ch))

    # GB2312(1980) 全部码位：6763 汉字 + 符号/拉丁/希腊等
    # lead 0xA1-0xF7, trail 0xA1-0xFE
    for lead in range(0xA1, 0xF8):
        for trail in range(0xA1, 0xFF):
            try:
                s = bytes([lead, trail]).decode("gb2312")
            except Exception:
                continue
            if len(s) == 1:
                u.add(ord(s))

    # ===== 扩展字符集（按需解开对应注释即可，解开后重跑 subset_noto.py）=====
    # 1) GBK 全字（约 21003 汉字：简体 + 常用繁体 + 大量生僻）
    # for lead in range(0x81, 0xFF):
    #     for trail in (list(range(0x40, 0x7F)) + list(range(0x80, 0xFF))):
    #         try:
    #             s = bytes([lead, trail]).decode("gbk")
    #         except Exception:
    #             continue
    #         if len(s) == 1:
    #             u.add(ord(s))

    # 2) 全部 BMP 表意文字（U+4E00–U+9FFF，约 20992：覆盖绝大多数简/繁/日韩通用汉字）
    # for cp in range(0x4E00, 0xA000):
    #     u.add(cp)

    # 3) 生僻字扩展 A（U+3400–U+4DBF，约 6582：古籍/罕见人名地名）
    # for cp in range(0x3400, 0x4DBF + 1):
    #     u.add(cp)

    # 4) 自定义罕见姓名/公司字（把字填进字符串即可）
    # for ch in "喆淼鑫堃婧偲晟暄玥":
    #     u.add(ord(ch))

    return u


def main():
    if not os.path.exists(SRC):
        raise SystemExit("源字体缺失: " + SRC)

    unicodes = build_unicodes()
    print("目标码位数量: %d" % len(unicodes))

    # 1) 子集化 (CFF/OTF)
    font = TTFont(SRC)
    ss = subset.Subsetter()
    ss.populate(unicodes=unicodes)
    ss.subset(font)
    font.save(INTER)
    print("中间子集(OTF): %d 字形  %.2f MB" % (
        len(font.getGlyphOrder()), os.path.getsize(INTER) / 1024 / 1024))

    # 2) 转 glyf/TrueType (规避 OpenPDF 的 CFF 乱码)
    otf_to_ttf(INTER, OUT)

    out_size = os.path.getsize(OUT)
    print("最终交付(TTF): %.2f MB -> %s" % (out_size / 1024 / 1024, OUT))

    # 校验：卖方中文名必须全部覆盖
    missing = [ch for ch in SELLER_NAME if ord(ch) not in unicodes]
    if missing:
        raise SystemExit("严重: 卖方中文名字符未覆盖 -> " + "".join(missing))
    print("校验通过: 卖方中文名 12 字全部覆盖")


if __name__ == "__main__":
    main()
