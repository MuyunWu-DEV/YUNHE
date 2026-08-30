package com.yunhe.website.crm.support;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.ColumnText;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.entity.Quotation;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 外贸单据 PDF 渲染器基类（PI / CI / PL 共用）。
 *
 * <p>本类承载所有「与具体单据无关」的通用能力，子类只需关心自己的版面与配色：
 * <ul>
 *   <li>字体基础设施：Carlito 四字重 + Noto Sans SC 子集（glyf/TrueType，CJK 用），
 *       统一的 {@link #textFont} 按是否含中文自动选字；中文字体走 CIDFontType2 + CIDToGIDMap=/Identity，
 *       开启 OpenPDF 子集化（setSubset(true)）以压小体积——乱码根因在 CFF/OTF 路径，glyf 路径下子集器正常。</li>
 *   <li>字号 / 布局常量、{@link #fmtMoney} / {@link #safe} 等纯函数、{@link #resolveGroups} 等单据无关助手。</li>
 *   <li>底层 cell / phrase / paragraph 工厂；边框颜色统一走可覆写的 {@link #borderColor()}（PI=NAVY，CI=BLACK）。</li>
 *   <li>页脚框架 {@link FooterEvent}（声明句 + 横线 + 公司名 | Page X of Y），颜色与文案由子类在构造时传入。</li>
 * </ul>
 *
 * <p>配色策略：基类只定义 BLACK / WHITE 两个中性色；具体调色板（NAVY/GOLD/TXT…）由各子类定义，
 * 并通过工厂方法的颜色参数传入，从而把同一套版面代码复用在黑白色（CI）与彩色（PI）两种风格上。</p>
 */
@Slf4j
public abstract class AbstractTradePdfRenderer {

    // ===================== 字体（静态注册，随 jar 打包，规避运行环境缺字体） =====================
    protected static final String FONT_DIR = "/fonts/";
    protected static final String CJK_FONT = "NotoSansSC-Subset.ttf"; // SIL OFL 开源，GB2312 常用字子集，glyf/TrueType
    protected static final BaseFont CARLITO = initFont("carlito-regular.ttf");
    protected static final BaseFont CARLITO_B = initFont("carlito-bold.ttf");
    protected static final BaseFont CARLITO_I = initFont("carlito-italic.ttf");
    protected static final BaseFont CARLITO_BI = initFont("carlito-bolditalic.ttf");
    protected static final BaseFont CJK = initCjkFont();
    // Noto Sans SC 无独立 Bold 字重文件，粗体中文回退常规字重，故与 CJK 共用同一字体（避免重复加载）
    protected static final BaseFont CJK_B = CJK;

    // ===================== 中性色（所有单据通用） =====================
    protected static final Color BLACK = Color.BLACK;
    protected static final Color WHITE = Color.WHITE;

    // ===================== 字号档（语义分组，避免裸写魔法值） =====================
    protected static final float FS_MICRO = 6.5f;  // 页脚 / 单位标签 / 签名标签
    protected static final float FS_LABEL = 9f;  // 字段标签：EXPORTER / DATE: / Name: / HS Code: / 列头
    protected static final float FS_BODY  = 9f;  // 内容与值：地址 / 描述 / 数量 / 单价 / 金额 / SAY
    protected static final float FS_H2    = 9.5f;  // 分区标题：Terms / Bank Info / PAYMENT TERMS
    protected static final float FS_TITLE = 10.0f; // 顶部英文公司名
    protected static final float FS_H1    = 16.0f; // 单据英文全称（PROFORMA INVOICE / COMMERCIAL INVOICE）

    // ===================== 布局常量（所有单据通用） =====================
    protected static final float PAGE_MARGIN = 36f;       // A4 页边距（四边）
    protected static final float BORDER_WIDTH = 0.6f;     // 表格/卡片边框宽度
    protected static final float PAD = 3f;           // 统一表格单元格内边距（四边一致，PI/CI 通用）
    // ===================== 行距倍数（相对字号 size），集中管理以便 PI/CI 一致 =====================
    protected static final float LEADING = 1.2f;  // 统一行距（相对字号）：正文/标签/区块内容均为 1.2×，PI/CI 一致
    protected static final float FOOTER_DECL_GAP = 4f;    // 页脚声明句距底边
    protected static final float FOOTER_LINE_GAP = 11f;   // 页脚横线距底边
    protected static final float FOOTER_PAGE_GAP = 18f;   // 页脚「公司名 | Page」距底边
    protected static final float FOOTER_TPL_W = 30f;      // 总页数占位模板宽
    protected static final float FOOTER_TPL_H = 12f;      // 总页数占位模板高
    protected static final String DEFAULT_CCY = "USD";    // 缺省币种

    // ===================== 字体基础设施 =====================

    /** 从 classpath 读取字体文件字节（resources/fonts/ 下），规避对运行机 C:/Windows/Fonts 的依赖 */
    protected static byte[] readFontBytes(String fileName) throws IOException {
        try (InputStream is = AbstractTradePdfRenderer.class.getResourceAsStream(FONT_DIR + fileName)) {
            if (is == null) {
                throw new IOException("字体资源未找到: " + FONT_DIR + fileName);
            }
            return is.readAllBytes();
        }
    }

    /** 英文/数字字体（Carlito 各字重，SIL OFL 开源），从 jar 内 resources 加载 */
    private static BaseFont initFont(String fileName) {
        try {
            byte[] bytes = readFontBytes(fileName);
            return BaseFont.createFont(fileName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (Exception e) {
            log.warn("Carlito 字体加载失败，回退 Helvetica：{}", fileName, e);
            return null;
        }
    }

    /**
     * 中文 CID 字体：嵌入 Noto Sans SC 子集（glyf/TrueType，SIL OFL 开源，GB2312 常用字子集）。
     * 开启 OpenPDF 子集化（setSubset(true)）：glyf/TrueType 字体在 OpenPDF 子集器工作正常（与 Carlito 一致），
     * 仅把文档实际用到的中文字形嵌入 PDF，体积与"微软字体子集化"相当、远小于完整嵌入。
     * <p>为什么用 glyf/TrueType 而非原 OTF/CFF：OpenPDF 把 CFF/OTF 以 CIDFontType0 嵌入，在关闭子集化时
     * 直接写入原 CFF 的 CID 映射，而 Identity-H 编码把字符码当作 CID，与原 CFF 内部 CID 排序不一致，
     * 导致 CID→GID 映射错乱、中文渲染成<strong>错字乱码</strong>（非方块）。改用 glyf/TrueType 后走 CIDFontType2 +
     * CIDToGIDMap=/Identity（与 Carlito 完全相同的路径），且 OpenPDF 可正确子集化，中文既正确又小巧。
     * <p>代价：子集仅含用到的字，若日后某份单据出现 GB2312 之外的新汉字，需重跑 subset_noto.py 扩字符集再生成。
     * <p>若 Noto 加载失败，回退到 OpenPDF 内置 CJK 字体 STSong-Light（不嵌入，依赖阅读器自带 Adobe-GB1 中文字体）。
     */
    private static BaseFont initCjkFont() {
        try {
            byte[] bytes = readFontBytes(CJK_FONT);
            BaseFont bf = BaseFont.createFont(CJK_FONT, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
            bf.setSubset(true); // 开启子集化：仅嵌入用到的中文字形，PDF 保持小巧（glyf 路径下子集器正常）
            return bf;
        } catch (Exception e) {
            log.error("中文字体 Noto Sans SC 加载失败，回退 STSong-Light（不嵌入，依赖阅读器自带中文字体）", e);
            try {
                return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            } catch (Exception e2) {
                log.error("STSong-Light 亦加载失败，中文将无可用字体（最终回退 Helvetica 会变方块），请检查字体资源是否随 jar 打包", e2);
                return null;
            }
        }
    }

    protected static boolean hasCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            int cp = s.codePointAt(i);
            if ((cp >= 0x2E80 && cp <= 0x9FFF)
                    || (cp >= 0x3000 && cp <= 0x303F)
                    || (cp >= 0xFF00 && cp <= 0xFFEF)
                    || (cp >= 0x3040 && cp <= 0x30FF)) {
                return true;
            }
        }
        return false;
    }

    /** 按是否含中文选择字体；英文靠 Carlito 各文件名承载字重，中文统一用 Noto Sans SC（无独立粗体，粗体中文回退常规字重） */
    protected static Font textFont(String text, float size, int style, Color color) {
        BaseFont bf;
        if (hasCjk(text)) {
            bf = (style & Font.BOLD) != 0 ? (CJK_B != null ? CJK_B : CJK) : CJK;
        } else {
            boolean bold = (style & Font.BOLD) != 0;
            boolean italic = (style & Font.ITALIC) != 0;
            if (bold && italic) {
                bf = CARLITO_BI != null ? CARLITO_BI : (CARLITO_B != null ? CARLITO_B : CARLITO);
            } else if (bold) {
                bf = CARLITO_B != null ? CARLITO_B : CARLITO;
            } else if (italic) {
                bf = CARLITO_I != null ? CARLITO_I : CARLITO;
            } else {
                bf = CARLITO;
            }
        }
        if (bf == null) {
            return new Font(Font.HELVETICA, size, style, color);
        }
        // 字重由 bf 选择（不同字重文件承载），不依赖 Font.style；故此处统一用 NORMAL（仅 Helvetica 兜底分支才用 style）
        return new Font(bf, size, Font.NORMAL, color);
    }

    // ===================== 边框色钩子（子类覆写） =====================

    /** 表格/卡片边框颜色。基类默认黑；PI 覆写为 NAVY，CI 保持黑。 */
    protected Color borderColor() {
        return BLACK;
    }

    /** 边框线宽（子类可覆写以加粗/减细；默认与 {@link #BORDER_WIDTH} 一致，PI 不受影响） */
    protected float borderWidth() {
        return BORDER_WIDTH;
    }

    // ===================== 底层 cell / phrase / paragraph 工厂 =====================

    protected static Paragraph blank(float pt) {
        Paragraph p = new Paragraph(" ", textFont(" ", FS_MICRO, Font.NORMAL, BLACK));
        p.setLeading(pt);
        return p;
    }

    /** 统一包裹 doc.add，消除各 render 方法重复的 try/catch 样板 */
    protected static void add(Document doc, Element e) {
        try {
            doc.add(e);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /** 通用单元格：文本经 textFont 选字；边框色走 {@link #borderColor()} */
    protected PdfPCell cell(String text, float size, int style, Color color, Color bg,
                            int hAlign, float padding, float borderWidth) {
        Phrase ph = new Phrase(safe(text), textFont(text, size, style, color));
        ph.setLeading(size);
        PdfPCell c = new PdfPCell(ph);
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(hAlign);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setUseAscender(true);
        c.setUseDescender(true);
        c.setPaddingLeft(padding);
        c.setPaddingRight(padding);
        c.setPaddingTop(padding);
        c.setPaddingBottom(padding);
        c.setBorderWidth(borderWidth);
        if (borderWidth > 0) {
            c.setBorderColor(borderColor());
        }
        return c;
    }

    /** 承载多色 Phrase 的单元格（如 标签+值、金额区） */
    protected PdfPCell phraseCell(Phrase phrase, int hAlign, float padding, float borderWidth, Color bg) {
        phrase.setLeading(phrase.getFont().getSize()); // 收紧行盒，消除 MIDDLE 居中时上边距大于下边距
        PdfPCell c = new PdfPCell(phrase);
        c.setHorizontalAlignment(hAlign);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setUseAscender(true);
        c.setUseDescender(true);
        c.setPaddingLeft(padding);
        c.setPaddingRight(padding);
        c.setPaddingTop(padding);
        c.setPaddingBottom(padding);
        c.setBorderWidth(borderWidth);
        if (borderWidth > 0) {
            c.setBorderColor(borderColor());
        }
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    /** 带边框 + 固定内边距的卡片单元格（Seller/Buyer/EXPORTER/CONSIGNEE 复用） */
    protected PdfPCell borderedCardCell() {
        PdfPCell c = new PdfPCell();
        c.setBorderWidth(borderWidth());
        c.setBorderColor(borderColor());
        c.setVerticalAlignment(Element.ALIGN_TOP); // 钉顶对齐，避免内容行数不同的卡片被垂直居中导致左右标签错位
        c.setPadding(PAD);
        return c;
    }

    protected static Paragraph para(String text, float size, int style, Color color, float spacingAfter) {
        Paragraph p = new Paragraph(safe(text), textFont(text, size, style, color));
        p.setLeading(0, LEADING); // 统一正文行距（与 boldParagraph/kvLineParagraph 一致），去除默认 1.5× 的松散余量
        if (spacingAfter > 0) p.setSpacingAfter(spacingAfter);
        return p;
    }

    protected static Paragraph boldParagraph(String text, float size, float spacingAfter, Color color) {
        Paragraph p = new Paragraph(safe(text), textFont(text, size, Font.BOLD, color));
        p.setLeading(0, LEADING);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    /** 两色短语：label（指定样式/颜色）+ value（指定颜色） */
    protected Phrase kvPhrase(String label, String value, int labelStyle, Color labelColor, Color valueColor) {
        Phrase p = new Phrase();
        p.add(new Chunk(label, textFont(label, FS_LABEL, labelStyle, labelColor)));
        p.add(new Chunk(value, textFont(value, FS_BODY, Font.NORMAL, valueColor)));
        return p;
    }

    /** 联系信息单行：label（指定样式/颜色）+ value（指定颜色），紧凑行距 */
    protected Paragraph kvLineParagraph(String label, String value, int labelStyle, Color labelColor, Color valueColor) {
        Paragraph p = new Paragraph();
        p.setLeading(0, LEADING);
        p.add(new Chunk(safe(label), textFont(label, FS_LABEL, labelStyle, labelColor)));
        p.add(new Chunk(safe(value), textFont(value, FS_BODY, Font.NORMAL, valueColor)));
        p.add(new Chunk(" ", textFont(" ", FS_BODY, Font.NORMAL, valueColor)));
        p.setSpacingAfter(2);
        return p;
    }

    /** 表头单元格（bg/fg 由子类决定：PI=NAVY/WHITE，CI=BLACK/WHITE） */
    protected PdfPCell headerCell(String text, Color bg, Color fg, float size) {
        return cell(text, size, Font.BOLD, fg, bg, Element.ALIGN_CENTER, PAD, borderWidth());
    }

    /** 货物表首列：Name: + 名称 + HS Code: + 编码（标签色与值色由子类决定） */
    protected PdfPCell nameCell(String name, String hsCode, int rowspan, Color labelColor, Color valueColor) {
        Phrase ph = new Phrase();
        ph.add(new Chunk("Name:\n", textFont("Name: ", FS_LABEL, Font.BOLD | Font.ITALIC, labelColor)));
        ph.add(new Chunk(safe(name) + "\n", textFont(safe(name), FS_BODY, Font.NORMAL, valueColor)));
        ph.add(new Chunk("\n", textFont(" ", FS_MICRO, Font.NORMAL, valueColor)));   // 矮空白行：留一点缝
        ph.add(new Chunk("HS Code:\n", textFont("HS Code:", FS_LABEL, Font.BOLD | Font.ITALIC, labelColor)));
        ph.add(new Chunk(safe(hsCode), textFont(safe(hsCode), FS_BODY, Font.NORMAL, valueColor)));
        PdfPCell c = phraseCell(ph, Element.ALIGN_LEFT, PAD, borderWidth(), null);
        if (rowspan > 1) c.setRowspan(rowspan);
        return c;
    }

    /** 货物表描述单元格：名称（nameColor 加粗）+ 换行描述（descColor） */
    protected PdfPCell detailCell(String name, String desc, Color nameColor, Color descColor) {
        PdfPCell c = new PdfPCell();
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(PAD);
        c.setBorderWidth(borderWidth());
        c.setBorderColor(borderColor());

        String safeDesc = safe(desc);
        String safeName = safe(name);
        if (safeDesc.isEmpty()) {
            c.addElement(blank(9));
            return c;
        }
        if (!safeName.isEmpty()) {
            Paragraph p = new Paragraph(safeName, textFont(safeName, FS_BODY, Font.BOLD, nameColor));
            p.setAlignment(Element.ALIGN_LEFT);
            p.setLeading(0, LEADING);
            p.setSpacingAfter(3f);
            c.addElement(p);
        }
        // 按换行拆成独立 Paragraph 逐个 addElement（OpenPDF 对含 \n 的单 Paragraph
        // setLeading 不生效，必须拆行后各自控制 leading + spacingAfter）
        String[] lines = safeDesc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Paragraph p = new Paragraph(line.isBlank() ? " " : line,
                    textFont(line, FS_BODY, Font.NORMAL, descColor));
            p.setAlignment(Element.ALIGN_LEFT);
            p.setLeading(0, LEADING); // 行高（控制换行后的子行间距）
            if (i < lines.length - 1) {
                p.setSpacingAfter(2f); // 逻辑行之间的额外间距（description 行距）
            }
            c.addElement(p);
        }
        return c;
    }

    protected PdfPCell qtyCell(int qty, String unit, Color numColor, Color unitColor) {
        Phrase ph = new Phrase();
        ph.add(new Chunk(String.valueOf(qty) + "\n", textFont(String.valueOf(qty), FS_BODY, Font.BOLD, numColor)));
        ph.add(new Chunk(safe(unit), textFont(safe(unit), FS_MICRO, Font.NORMAL, unitColor)));
        return phraseCell(ph, Element.ALIGN_CENTER, PAD, borderWidth(), WHITE);
    }

    protected PdfPCell unitPriceCell(BigDecimal price, String unit, String ccy, Color numColor, Color unitColor) {
        Phrase ph = new Phrase();
        ph.add(new Chunk((price != null ? fmtMoney(price) : "-") + "\n",
                textFont(price != null ? fmtMoney(price) : "-", FS_BODY, Font.BOLD, numColor)));
        ph.add(new Chunk(safe(ccy) + " / " + safe(unit), textFont(safe(ccy), FS_MICRO, Font.NORMAL, unitColor)));
        return phraseCell(ph, Element.ALIGN_CENTER, PAD, borderWidth(), WHITE);
    }

    protected PdfPCell totalPriceCell(BigDecimal total, String ccy, Color numColor, Color unitColor) {
        Phrase ph = new Phrase();
        ph.add(new Chunk((total != null ? fmtMoney(total) : "-") + "\n",
                textFont(total != null ? fmtMoney(total) : "-", FS_BODY, Font.BOLD, numColor)));
        ph.add(new Chunk(safe(ccy), textFont(safe(ccy), FS_MICRO, Font.NORMAL, unitColor)));
        return phraseCell(ph, Element.ALIGN_CENTER, PAD, borderWidth(), WHITE);
    }

    // ===================== 单据无关助手 =====================

    /** 从报价单解析货物分组（PI/CI 均经此取明细） */
    protected static List<QuoteDetailGroup> resolveGroups(Quotation q) {
        if (q == null || q.getDetails() == null) return List.of();
        return q.getDetails();
    }

    protected static int totalQty(List<QuoteDetailGroup> groups) {
        if (groups == null) return 0;
        return groups.stream()
                .filter(g -> g.items() != null)
                .flatMap(g -> g.items().stream())
                .mapToInt(QuoteDetailItem::quantity).sum();
    }

    protected static String mainUnit(List<QuoteDetailGroup> groups) {
        if (groups == null) return "";
        return groups.stream()
                .filter(g -> g.items() != null && !g.items().isEmpty())
                .flatMap(g -> g.items().stream())
                .map(QuoteDetailItem::unit)
                .filter(u -> u != null && !u.isBlank())
                .findFirst().orElse("");
    }

    protected static Map<String, String> parseTerms(String terms) {
        Map<String, String> map = new LinkedHashMap<>();
        if (terms == null || terms.isBlank()) return map;
        String[] labels = {"Country of Origin", "Port of Delivery", "Time of Delivery",
                "Payment Term", "Packing", "Note"};
        String[] lines = terms.split("\\r?\\n");
        List<String> extras = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            boolean matched = false;
            for (String label : labels) {
                if (trimmed.regionMatches(true, 0, label, 0, label.length())
                        && trimmed.length() > label.length()
                        && trimmed.charAt(label.length()) == ':') {
                    map.put(label, trimmed.substring(label.length() + 1).trim());
                    matched = true;
                    break;
                }
            }
            if (!matched) extras.add(trimmed);
        }
        if (!extras.isEmpty()) {
            map.put("_extra", String.join("\n", extras));
        }
        return map;
    }

    protected static Map<String, String> parseKvByFirstColon(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return map;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int idx = trimmed.indexOf(':');
            if (idx > 0) {
                map.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
            } else {
                map.put(trimmed, "");
            }
        }
        return map;
    }

    protected static String fmtMoney(BigDecimal v) {
        if (v == null) return "0";
        // 无小数时不显示 .00
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() <= 0) {
            return String.format("%,d", s.toBigInteger());
        }
        return String.format("%,.2f", v);
    }

    protected static String safe(String s) {
        return s == null ? "" : s;
    }

    protected static String sellerCompanyName(ProformaDetails.SellerInfo seller) {
        if (seller == null || seller.companyName() == null || seller.companyName().isBlank()) {
            return "";
        }
        return seller.companyName().trim();
    }

    protected static String sellerChineseName(ProformaDetails.SellerInfo seller) {
        if (seller == null || seller.chineseName() == null || seller.chineseName().isBlank()) {
            return "";
        }
        return seller.chineseName().trim();
    }

    // ===================== 金额大写（英文，CI 等单据用） =====================

    private static final String[] WORDS_UNITS = {"", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE",
            "TEN", "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN"};
    private static final String[] WORDS_TENS = {"", "", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY"};
    private static final String[] WORDS_SCALES = {"", " THOUSAND", " MILLION", " BILLION", " TRILLION"};

    /** 0–99 → 英文（十位与个位间用连字符，如 SEVENTY-NINE，贴合单据惯例） */
    private static String wordsTwoDigits(int n) {
        if (n < 20) return WORDS_UNITS[n];
        int t = n / 10;
        int u = n % 10;
        return WORDS_TENS[t] + (u > 0 ? "-" + WORDS_UNITS[u] : "");
    }

    /** 0–999 → 英文 */
    private static String wordsThreeDigits(int n) {
        if (n == 0) return "";
        int h = n / 100;
        int r = n % 100;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(WORDS_UNITS[h]).append(" HUNDRED");
        if (r > 0) {
            if (h > 0) sb.append(" ");
            sb.append(wordsTwoDigits(r));
        }
        return sb.toString();
    }

    /** 非负长整数 → 英文金额（仅数字部分，不含币种词） */
    protected static String numberToWords(long n) {
        if (n == 0) return "ZERO";
        if (n < 0) return "NEGATIVE " + numberToWords(-n);
        StringBuilder sb = new StringBuilder();
        int scaleIdx = 0;
        while (n > 0) {
            int chunk = (int) (n % 1000);
            if (chunk > 0) {
                String words = wordsThreeDigits(chunk) + WORDS_SCALES[scaleIdx];
                sb.insert(0, words + (sb.length() > 0 ? " " : ""));
            }
            n /= 1000;
            scaleIdx++;
        }
        return sb.toString().trim();
    }

    /** 币种码 → 英文币种词（USD→US DOLLARS 等） */
    protected static String currencyWords(String ccy) {
        if (ccy == null) ccy = DEFAULT_CCY;
        return switch (ccy.toUpperCase()) {
            case "USD" -> "US DOLLARS";
            case "EUR" -> "EUROS";
            case "GBP" -> "POUNDS STERLING";
            case "INR" -> "INDIAN RUPEES";
            case "JPY" -> "YEN";
            default -> ccy.toUpperCase() + " DOLLARS";
        };
    }

    /**
     * 金额 → 英文大写（币种词在前，如 "US DOLLARS ONE HUNDRED SEVENTY-NINE THOUSAND FOUR HUNDRED"）。
     * 单据通常以 "SAY " + 本结果 + " ONLY." 收尾。无小数时仅写币种整额；含分时在末尾追加 " AND xx CENTS"。
     */
    protected static String amountInWordsEn(BigDecimal amount, String ccy) {
        if (amount == null) amount = BigDecimal.ZERO;
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        long cents = scaled.multiply(BigDecimal.valueOf(100)).longValue();
        long dollars = cents / 100;
        int rem = (int) (cents % 100);
        String ccyWords = currencyWords(ccy);
        StringBuilder sb = new StringBuilder(ccyWords).append(" ").append(numberToWords(dollars));
        if (rem > 0) {
            sb.append(" AND ").append(numberToWords(rem)).append(" CENTS");
        }
        return sb.toString();
    }

    // ===================== 页脚事件（子类构造时传入文案与颜色） =====================

    /**
     * 每页自动绘制「声明句 + 横线 + 公司名 | Page X of Y」。
     * X 取自 writer.getPageNumber()（自动从 1 起）；Y 用 PdfTemplate 占位，onCloseDocument 时写入真实总页数。
     */
    protected static final class FooterEvent extends PdfPageEventHelper {
        private final String declaration;
        private final String companyName;
        private final Color textColor;
        private final Color lineColor;
        private PdfTemplate totalTP;

        FooterEvent(String companyName, String declaration, Color textColor, Color lineColor) {
            this.companyName = companyName == null ? "" : companyName;
            this.declaration = declaration == null ? "" : declaration;
            this.textColor = textColor == null ? BLACK : textColor;
            this.lineColor = lineColor == null ? BLACK : lineColor;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalTP = writer.getDirectContent().createTemplate(FOOTER_TPL_W, FOOTER_TPL_H);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float left = document.left();
            float right = document.right();
            float bottom = document.bottom();
            float cx = (left + right) / 2f;

            // 1) 声明句（居中）
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase(declaration, textFont(declaration, FS_MICRO, Font.ITALIC, textColor)),
                    cx, bottom - FOOTER_DECL_GAP, 0);

            // 2) 横线（满正文宽）
            cb.saveState();
            cb.setColorStroke(lineColor);
            cb.setLineWidth(0.5f);
            float lineY = bottom - FOOTER_LINE_GAP;
            cb.moveTo(left, lineY);
            cb.lineTo(right, lineY);
            cb.stroke();
            cb.restoreState();

            // 3) 公司名 | Page X of Y
            int page = writer.getPageNumber();
            String lead = companyName + "  |  Page " + page + " of ";
            Font footFont = textFont(lead, FS_MICRO, Font.NORMAL, textColor);
            float leadW = footFont.getBaseFont().getWidthPoint(lead, FS_MICRO);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase(lead, footFont), cx, bottom - FOOTER_PAGE_GAP, 0);
            cb.addTemplate(totalTP, cx + leadW / 2f, bottom - FOOTER_PAGE_GAP);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            int total = writer.getPageNumber() - 1;
            ColumnText.showTextAligned(totalTP, Element.ALIGN_LEFT,
                    new Phrase(" " + total, textFont(String.valueOf(total), FS_MICRO, Font.NORMAL, textColor)),
                    0, 0, 0);
        }
    }
}
