#!/usr/bin/env python3
"""
将 Noto Sans SC 子集 OTF(CFF) 转换为 glyf 型 TrueType(.ttf)。

目的：OpenPDF 把 CFF/OTF 以 CIDFontType0 嵌入，在关闭子集化(setSubset=false)时
会因 CID->GID 映射错乱导致中文渲染成乱码(错字，非方块)。而 glyf/TrueType 在 OpenPDF
中以 CIDFontType2 + CIDToGIDMap=/Identity 嵌入(与已正常工作的 Carlito 同路径)，中文正确。

输入：subset_noto.py 产出的 NotoSansSC-Subset.otf (GB2312 常用字子集, 约 1.83MB)
输出：NotoSansSC-Subset.ttf (glyf, 同子集), 供 PiPdfRenderer 改用。

依赖：fontTools (pip install fonttools)
"""
import sys
from fontTools.ttLib import TTFont, newTable
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen

MAX_ERR = 1.0          # 三次曲线->二次曲线最大误差(字体单位)，越小越精确越大
POST_FORMAT = 3.0      # 3.0=丢弃字形名(省空间)，嵌入无需字形名


def otf_to_ttf(otf_path: str, ttf_path: str) -> None:
    font = TTFont(otf_path)
    if font.sfntVersion != "OTTO":
        raise SystemExit(f"输入不是 OTF/CFF 字体 (sfntVersion={font.sfntVersion!r}): {otf_path}")

    glyph_order = font.getGlyphOrder()
    glyph_set = font.getGlyphSet()

    glyf = newTable("glyf")
    glyf.glyphOrder = glyph_order
    glyf.glyphs = {}
    for name in glyph_order:
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, MAX_ERR))
        glyf.glyphs[name] = pen.glyph()

    loca = newTable("loca")
    font["loca"] = loca
    font["glyf"] = glyf

    # 删除 CFF 相关表
    for t in ("CFF ", "VORG"):
        if t in font:
            del font[t]

    # maxp 升到 1.0(支持 glyf)
    maxp = font["maxp"]
    maxp.tableVersion = 0x00010000
    maxp.maxZones = 1
    maxp.maxTwilightPoints = 0
    maxp.maxStorage = 0
    maxp.maxFunctionDefs = 0
    maxp.maxInstructionDefs = 0
    maxp.maxStackElements = 0
    maxp.maxSizeOfInstructions = 0
    maxp.maxComponentElements = getattr(maxp, "maxComponentElements", 0)

    # post 丢弃字形名省空间
    font["post"].formatType = POST_FORMAT

    # 设为 TrueType 签名
    font.sfntVersion = "\x00\x01\x00\x00"

    font.save(ttf_path)


def main() -> None:
    src = sys.argv[1] if len(sys.argv) > 1 else "NotoSansSC-Subset.otf"
    dst = sys.argv[2] if len(sys.argv) > 2 else "NotoSansSC-Subset.ttf"
    otf_to_ttf(src, dst)
    out = TTFont(dst)
    assert out.sfntVersion == "\x00\x01\x00\x00", "输出非 TrueType"
    assert "glyf" in out and "loca" in out, "缺少 glyf/loca 表"
    n = len(out.getGlyphOrder())
    import os
    size = os.path.getsize(dst)
    print(f"OK  glyphs={n}  size={size:,}B ({size/1024/1024:.2f}MB)  -> {dst}")


if __name__ == "__main__":
    main()
