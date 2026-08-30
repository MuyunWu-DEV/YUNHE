# 字体资源（PI PDF 中文嵌入）

`PiPdfRenderer` 用 `NotoSansSC-Subset.ttf`（glyf/TrueType，~2MB）嵌入中文。
本目录用于**重新生成**该字体，非运行时代码。

## 文件
- `NotoSansSC-Subset.ttf` — 最终交付，随 jar 打包（渲染用）
- `_src/NotoSansSC-Regular.otf` — 全字库源（gitignore，不打包）
- `_src/NotoSansSC-Subset.otf` — 子集中间产物（gitignore）
- `subset_noto.py` — 子集化主脚本（裁字 + 调转换 → 产出 .ttf）
- `otf2ttf_noto.py` — OTF(CFF)→TTF(glyf) 转换（修乱码的关键一步）
- 验证脚本在 `WorkBuddy/YUNHE/verify_ttf_pdf.py`

## 重新生成字体
```bash
cd src/main/resources/fonts
python subset_noto.py          # 一条命令：子集 → 转换 → 产出 NotoSansSC-Subset.ttf
```

重新生成后重建 PDF：
```bash
mvn.cmd -o test -Dtest=PiPdfRendererTest     # 重出 pi_mock.pdf（WorkBuddy/YUNHE/）
python ../../../../../../WorkBuddy/YUNHE/verify_ttf_pdf.py   # 核对中文 cmap→GID→glyf 映射
```

## 扩充字符（如加繁体/生僻字）
编辑 `subset_noto.py` 的 `build_unicodes()` 增加码位，再跑上面的生成命令。

## 关键注意
- 必须是 **glyf/TrueType**；否则 OpenPDF 以 CIDFontType0 嵌 CFF → 中文乱码。
  下载的字体须先确认含 `glyf` 表再信任（部分 `.ttf` 实为 CFF 伪装）。
- 字符集现为 GB2312(1980) 6763 简体 + 卖方名 + 标点/全角/货币；GB2312 外的字仍缺（方块）。
