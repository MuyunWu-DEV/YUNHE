package com.yunhe.website.crm.support;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.ProformaInvoice;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Proforma Invoice PDF 渲染器（严格对齐参考模板 Proforma Invoice - V1.0 的实测样式）。
 *
 * <p>字体规范（全部英文文本只用这 3 种 Calibri 字体，靠字体名加粗/斜体，不靠 weight 位）：
 * <ul>
 *   <li>Calibri（常规）/ Calibri-Bold（加粗）/ Calibri-Italic（斜体）</li>
 *   <li>中文文本采用微软雅黑（msyh / msyhbd），由 {@link #textFont} 按是否含 CJK 自动切换</li>
 * </ul>
 *
 * <p>配色规范（与原模板逐一核对）：
 * <ul>
 *   <li>NAVY #1A3C5E：主文字 / 标题 / 加粗主体 / 深蓝带底色（其上反白字）</li>
 *   <li>GOLD #C9A227：大标题背景块 + 强调标签（S/B/WARRANTY/签名）+ 唯一 TOTAL 金额区</li>
 *   <li>TXT #1A1A1A：DATE/REF 值等近黑正文</li>
 *   <li>TXT2 #444444：Seller/Buyer 联系值、商品描述、条款值、银行值（灰度主体）</li>
 *   <li>TXT3 #666666：单位标签、页脚声明/公司名（三级灰）</li>
 *   <li>LIGHT_BLUE #B8C4D0：公司抬头中文名（斜体浅蓝，反白于深蓝带）</li>
 *   <li>ZONE #EAF1F8：TERMS 标签列底；CARD #F5F8FC：仅 TERMS 额外说明行的浅底（Seller/Buyer 与商品表体已改白底）</li>
 * </ul>
 *
 * <p>字号规范（严格采用原模板实测档位）：6.5 / 7.0 / 7.6 / 8.0 / 8.5 / 9.0 / 9.5 / 11.0 / 13.0 / 16.0。</p>
 */
@Slf4j
@Component
public class PiPdfRenderer {

    // ===================== 统一色板（与原模板核对） =====================
    private static final Color NAVY = new Color(26, 60, 94);       // #1A3C5E
    private static final Color GOLD = new Color(201, 162, 39);     // #C9A227
    private static final Color WHITE = Color.WHITE;
    private static final Color TXT = new Color(26, 26, 26);        // #1A1A1A 近黑
    private static final Color TXT2 = new Color(68, 68, 68);       // #444444 灰度主体
    private static final Color TXT3 = new Color(102, 102, 102);    // #666666 三级灰
    private static final Color LINE_STRONG = new Color(74, 107, 138);
    private static final Color LINE_LIGHT = new Color(138, 164, 190);
    private static final Color ZONE = new Color(234, 241, 248);    // #EAF1F8 TERMS 标签列
    private static final Color CARD = new Color(245, 248, 252);    // #F5F8FC 浅底
    private static final Color LIGHT_BLUE = new Color(184, 196, 208); // #B8C4D0 抬头中文名

    // ===================== 字号档（原模板实测） =====================
    private static final float FS_MICRO = 6.5f;  // 页脚 / 单位标签 / 签名说明
    private static final float FS_XS = 7.0f;     // 商品描述 / 签名标签
    private static final float FS_S = 7.6f;      // SELLER/BUYER 标签 / WARRANTY / 银行标签
    private static final float FS_BODY = 8.0f;   // 地址电话邮箱 / 表头 / TERMS / 银行值
    private static final float FS_BL = 8.5f;     // Seller/Buyer 公司名 / TOTAL 标签
    private static final float FS_MID = 9.0f;    // Name:/HS Code: / TERMS·BANK 标题
    private static final float FS_REF = 9.5f;    // DATE / REF
    private static final float FS_TITLE = 11.0f; // 公司抬头 / 金额
    private static final float FS_QTY = 13.0f;   // 数量数字
    private static final float FS_H1 = 16.0f;    // 大标题

    // ===================== 字体（静态注册） =====================
    private static final BaseFont CALIBRI = initCalibri("C:/Windows/Fonts/calibri.ttf");
    private static final BaseFont CALIBRI_B = initCalibri("C:/Windows/Fonts/calibrib.ttf");
    private static final BaseFont CALIBRI_I = initCalibri("C:/Windows/Fonts/calibrii.ttf");
    private static final BaseFont CALIBRI_BI = initCalibri("C:/Windows/Fonts/calibriz.ttf");
    private static final BaseFont YAHEI = initCjk("C:/Windows/Fonts/msyh.ttc,0");
    private static final BaseFont YAHEI_B = initCjk("C:/Windows/Fonts/msyhbd.ttc,0");

    private static BaseFont initCalibri(String path) {
        try {
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            log.warn("Calibri 字体加载失败，回退 Helvetica：{}", path, e);
            return null;
        }
    }

    private static BaseFont initCjk(String path) {
        try {
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            log.warn("中文字体加载失败，回退 STSong-Light：{}", path, e);
            try {
                return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static boolean hasCjk(String s) {
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

    /** 按是否含中文选择字体；靠 BaseFont 本身承载字重（Calibri 用 calibrib/calibrii 名，中文用 msyhbd） */
    private static Font textFont(String text, float size, int style, Color color) {
        BaseFont bf;
        if (hasCjk(text)) {
            bf = (style & Font.BOLD) != 0 ? (YAHEI_B != null ? YAHEI_B : YAHEI) : YAHEI;
        } else {
            boolean bold = (style & Font.BOLD) != 0;
            boolean italic = (style & Font.ITALIC) != 0;
            if (bold && italic) {
                bf = CALIBRI_BI != null ? CALIBRI_BI : (CALIBRI_B != null ? CALIBRI_B : CALIBRI);
            } else if (bold) {
                bf = CALIBRI_B != null ? CALIBRI_B : CALIBRI;
            } else if (italic) {
                bf = CALIBRI_I != null ? CALIBRI_I : CALIBRI;
            } else {
                bf = CALIBRI;
            }
        }
        if (bf == null) {
            return new Font(Font.HELVETICA, size, style, color);
        }
        return new Font(bf, size, Font.NORMAL, color);
    }

    private static Paragraph blank(float pt) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(pt);
        return p;
    }

    /** 标题下方的金色细线（实心金色条，更细，无表格框） */
    private static PdfPTable goldLine() {
        PdfPTable sep = new PdfPTable(1);
        sep.setWidthPercentage(100);
        PdfPCell s = new PdfPCell(new Phrase(" "));
        s.setBackgroundColor(GOLD);
        s.setBorder(0);
        s.setFixedHeight(0.8f);
        sep.addCell(s);
        return sep;
    }

    // =====================================================================
    //  主流程
    // =====================================================================

    public byte[] render(ProformaInvoice invoice) {
        ProformaDetails details = invoice.getDetails();
        ProformaDetails.BuyerInfo buyer = details != null ? details.buyer() : null;

        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            renderCompanyHeader(doc, details);
            doc.add(blank(8));
            renderTitle(doc);
            doc.add(blank(8));
            renderDateRef(doc, invoice);
            doc.add(blank(8));
            renderSellerBuyer(doc, details, buyer);
            doc.add(blank(8));
            renderItemTable(doc, invoice);
            renderTotal(doc, invoice, details);
            doc.add(blank(4));
            renderTermsAndConditions(doc, details);
            doc.add(blank(4));
            renderBankAccount(doc, details);
            doc.add(blank(8));
            renderSignatures(doc, details, buyer);
            renderFooter(doc, details);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成 PI PDF 失败：" + e.getMessage(), e);
        }
    }

    // =====================================================================
    //  各段落（严格对齐原模板实测样式）
    // =====================================================================

    /** 1. 公司头部：深蓝带白字（公司英文名 + 中文名斜体浅蓝），无金色分隔线 */
    private void renderCompanyHeader(Document doc, ProformaDetails details) {
        String companyName = sellerCompanyName(details != null ? details.seller() : null);
        if (companyName.isEmpty()) {
            companyName = "SELLER";
        }

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.addCell(cell(companyName, FS_TITLE, Font.BOLD, WHITE, NAVY, Element.ALIGN_CENTER, 8, 0));
        String chineseName = sellerChineseName(details != null ? details.seller() : null);
        if (!chineseName.isEmpty()) {
            table.addCell(cell(chineseName, FS_BODY, Font.ITALIC, LIGHT_BLUE, NAVY, Element.ALIGN_CENTER, 4, 0));
        }
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 2. 大标题：PROFORMA INVOICE，金色背景 + 深蓝加粗字 */
    private void renderTitle(Document doc) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell c = cell("P R O F O R M A   I N V O I C E",
                FS_H1, Font.BOLD, NAVY, GOLD, Element.ALIGN_CENTER, 8, 0.5f);
        c.setBorder(0);
        c.setLeading(0, 1.0f);
        table.addCell(c);
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 3. DATE / REF. No. 双列（标签深蓝加粗 + 值近黑常规，均 9.5pt） */
    private void renderDateRef(Document doc, ProformaInvoice invoice) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        Phrase datePhrase = kvPhrase("DATE:  ", invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : "");
        Phrase refPhrase = kvPhrase("REF. No.:  ", safe(invoice.getInvoiceNumber()));
        table.addCell(phraseCell(datePhrase, Element.ALIGN_LEFT, 6, 0.5f, WHITE));
        table.addCell(phraseCell(refPhrase, Element.ALIGN_RIGHT, 6, 0.5f, WHITE));
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 4. SELLER / BUYER 双列（浅底卡片 + 弱边框；标签金色；公司名深蓝加粗；联系值标签深蓝加粗+值灰度） */
    private void renderSellerBuyer(Document doc, ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        ProformaDetails.SellerInfo seller = details != null ? details.seller() : null;

        // 左：SELLER
        PdfPCell sellerCell = new PdfPCell();
        sellerCell.setBorderWidth(0.6f);
        sellerCell.setBorderColor(NAVY);
        sellerCell.setPaddingLeft(10);
        sellerCell.setPaddingRight(4);
        sellerCell.setPaddingTop(2f);
        sellerCell.setPaddingBottom(4);
        sellerCell.addElement(labelParagraph("S E L L E R"));
        if (seller != null) {
            if (seller.companyName() != null && !seller.companyName().isBlank()) {
                sellerCell.addElement(boldParagraph(seller.companyName(), FS_BL, 2, NAVY));
            }
            if (seller.address() != null && !seller.address().isBlank())
                sellerCell.addElement(kvLineParagraph("Add: ", seller.address()));
            if (seller.phone() != null && !seller.phone().isBlank())
                sellerCell.addElement(kvLineParagraph("Tel: ", seller.phone()));
            if (seller.email() != null && !seller.email().isBlank())
                sellerCell.addElement(kvLineParagraph("Email: ", seller.email()));
        }
        table.addCell(sellerCell);

        // 右：BUYER
        PdfPCell buyerCell = new PdfPCell();
        buyerCell.setBorderWidth(0.6f);
        buyerCell.setBorderColor(NAVY);
        buyerCell.setPaddingLeft(10);
        buyerCell.setPaddingRight(4);
        buyerCell.setPaddingTop(2);
        buyerCell.setPaddingBottom(4);
        buyerCell.addElement(labelParagraph("B U Y E R"));
        if (buyer != null) {
            buyerCell.addElement(boldParagraph(safe(buyer.companyName()), FS_BL, 2, NAVY));
            if (buyer.registrationNo() != null && !buyer.registrationNo().isBlank())
                buyerCell.addElement(kvLineParagraph("Registration No.: ", buyer.registrationNo()));
            if (buyer.address() != null && !buyer.address().isBlank())
                buyerCell.addElement(kvLineParagraph("Add: ", buyer.address()));
        }
        table.addCell(buyerCell);

        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 5. ITEM 表格：深蓝表头白字（8pt），浅底表体；Name 加粗斜体深蓝；描述灰度；数量13pt；金额11pt */
    private void renderItemTable(Document doc, ProformaInvoice invoice) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.1f, 4.5f, 1f, 1.4f, 1.6f});

        table.addCell(headerCell("I T E M"));
        table.addCell(headerCell("C O M M O D I T Y  N A M E"));
        table.addCell(headerCell("Q T Y"));
        table.addCell(headerCell("U N I T  P R I C E"));
        table.addCell(headerCell("T O T A L  P R I C E"));

        List<QuoteDetailGroup> groups = resolveDetailGroups(invoice);
        if (groups == null || groups.isEmpty()) {
            table.addCell(cell("—", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, 6, 0.5f));
            table.addCell(cell("(无明细)", FS_XS, Font.ITALIC, TXT3, WHITE, Element.ALIGN_LEFT, 6, 0.5f));
            table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, 6, 0.5f));
            table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, 6, 0.5f));
            table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, 6, 0.5f));
        } else {
            for (QuoteDetailGroup g : groups) {
                List<QuoteDetailItem> items = g.items() != null ? g.items() : List.of();
                int groupSpan = Math.max(1, items.size());
                int i = 0;
                for (QuoteDetailItem it : items) {
                    if (i == 0) {
                        table.addCell(nameCell(g.name(), g.hsCode(), groupSpan));
                    }
                    table.addCell(detailCell(it.description()));
                    table.addCell(qtyCell(it.quantity(), it.unit()));
                    table.addCell(unitPriceCell(it.unitPrice(), it.unit(), it.currency()));
                    table.addCell(totalPriceCell(
                            it.unitPrice() != null && it.quantity() > 0
                                    ? it.unitPrice().multiply(BigDecimal.valueOf(it.quantity())) : null,
                            it.currency()));
                    i++;
                }
                if (items.isEmpty()) {
                    table.addCell(nameCell(g.name(), g.hsCode(), 1));
                    table.addCell(cell("—", FS_XS, Font.NORMAL, TXT3, WHITE, Element.ALIGN_LEFT, 6, 0.5f));
                    table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, 6, 0.5f));
                    table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, 6, 0.5f));
                    table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, 6, 0.5f));
                }
            }
        }
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 6. TOTAL：深蓝标签带 + 单一金色金额区（11pt 深蓝）+ 数量（白底深蓝）+ 可选 WARRANTY 整行（直接合并进本表） */
    private void renderTotal(Document doc, ProformaInvoice invoice, ProformaDetails details) {
        Map<String, BigDecimal> totalByCcy = new LinkedHashMap<>();
        List<QuoteDetailGroup> groups = resolveDetailGroups(invoice);
        if (groups != null) {
            for (QuoteDetailGroup g : groups) {
                if (g.items() == null) continue;
                for (QuoteDetailItem it : g.items()) {
                    if (it.unitPrice() == null || it.quantity() <= 0) continue;
                    String ccy = it.currency() != null ? it.currency() : "USD";
                    BigDecimal lineTotal = it.unitPrice().multiply(BigDecimal.valueOf(it.quantity()));
                    totalByCcy.merge(ccy, lineTotal, BigDecimal::add);
                }
            }
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.1f, 4.5f, 1f, 1.4f, 1.6f});
        String mainCcy = totalByCcy.keySet().stream().findFirst().orElse("USD");
        BigDecimal mainTotal = totalByCcy.getOrDefault(mainCcy, BigDecimal.ZERO);

        PdfPCell label = cell("T O T A L   —   F O B   C H I N A   P O R T",
                FS_BL, Font.BOLD, WHITE, NAVY, Element.ALIGN_CENTER, 4, 0.5f);
        label.setColspan(4);
        table.addCell(label);
        // 唯一金色区域（合并为一个框）：金额（FS_TITLE 深蓝，居中）+ 总数量（FS_MICRO 深蓝，居中）
        // 与 UNIT PRICE / TOTAL PRICE 列采用同一实现方式：单 Phrase（\n 换行）经 phraseCell 居中
        Phrase totalPh = new Phrase();
        totalPh.add(new Chunk(fmtMoney(mainTotal) + " " + mainCcy + "\n",
                textFont(fmtMoney(mainTotal) + mainCcy, FS_TITLE, Font.BOLD, NAVY)));
        totalPh.add(new Chunk(totalQty(groups) + " " + mainUnit(groups),
                textFont(mainUnit(groups), FS_MICRO, Font.NORMAL, TXT3)));
        PdfPCell totalCell = phraseCell(totalPh, Element.ALIGN_CENTER, 6, 0.5f, GOLD);
        totalCell.setColspan(1);
        table.addCell(totalCell);

        // WARRANTY 直接合并进 TOTAL 表（整行，金标签 + 灰值，弱边框白底）
        String warranty = details != null ? details.warranty() : null;
        if (warranty != null && !warranty.isBlank()) {
            Phrase wPh = new Phrase();
            wPh.add(new Chunk("W A R R A N T Y    ", textFont("W A R R A N T Y", FS_S, Font.BOLD, GOLD)));
            wPh.add(new Chunk(safe(warranty), textFont(warranty, FS_S, Font.NORMAL, TXT2)));
            PdfPCell wCell = phraseCell(wPh, Element.ALIGN_LEFT, 5, 0.5f, WHITE);
            wCell.setColspan(5);
            table.addCell(wCell);
        }

        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 8. TERMS & CONDITIONS：深蓝文字标题（无表格框，下方金色线）+ 标签列浅蓝底 + 值灰度 */
    private void renderTermsAndConditions(Document doc, ProformaDetails details) {
        String title = "T E R M S   &   C O N D I T I O N S";
        Map<String, String> rows = parseTerms(details != null ? details.terms() : null);

        doc.add(para(title, FS_MID, Font.BOLD, NAVY, 3));
        doc.add(goldLine());
        doc.add(blank(4));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        String[] labels = {"Country of Origin", "Port of Delivery", "Time of Delivery",
                "Payment Term", "Packing", "Note"};
        for (String label : labels) {
            table.addCell(cell(label, FS_BODY, Font.BOLD, NAVY, ZONE, Element.ALIGN_LEFT, 3, 0.5f));
            table.addCell(cell(safe(rows.get(label)), FS_BODY, Font.NORMAL, TXT2, WHITE, Element.ALIGN_LEFT, 3, 0.5f));
        }
        if (rows.containsKey("_extra")) {
            PdfPCell extra = cell(rows.get("_extra"), FS_BODY, Font.ITALIC, TXT2, CARD, Element.ALIGN_LEFT, 3, 0.5f);
            extra.setColspan(2);
            table.addCell(extra);
        }
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 9. BANK ACCOUNT：深蓝文字标题（无表格框，下方金色线）+ 标签行深蓝带/白底交替，值灰度 */
    private void renderBankAccount(Document doc, ProformaDetails details) {
        String title = "B A N K   A C C O U N T   I N F O R M A T I O N";
        doc.add(para(title, FS_MID, Font.BOLD, NAVY, 3));
        doc.add(goldLine());
        doc.add(blank(4));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        String[] labels = {"Bank Name", "Bank Address", "SWIFT Code", "Account No.",
                "Beneficiary Name", "Beneficiary Address"};
        Map<String, String> kv = parseKvByFirstColon(details != null ? details.bankAccountInformation() : null);
        for (String label : labels) {
            table.addCell(cell(label, FS_BODY, Font.BOLD, NAVY, ZONE, Element.ALIGN_LEFT, 3, 0.5f));
            table.addCell(cell(safe(kv.get(label)), FS_BODY, Font.NORMAL, TXT2, WHITE, Element.ALIGN_LEFT, 3, 0.5f));
        }
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        doc.add(blank(4));
    }

    /** 10. 签名区：金色 "F o r..." 标签 + 深蓝公司名 + 斜体说明（浅蓝外框） */
    private void renderSignatures(Document doc, ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        PdfPCell left = new PdfPCell();
        left.setBorderWidth(0.6f);
        left.setBorderColor(NAVY);
        left.setPaddingLeft(10);
        left.setPaddingRight(4);
        left.setPaddingTop(2);
        left.setPaddingBottom(4);
        left.addElement(para("F o r   &   o n   b e h a l f   o f   S E L L E R", FS_XS, Font.BOLD, GOLD, 2));
        left.addElement(boldParagraph(sellerCompanyName(details != null ? details.seller() : null), FS_BODY, 4, NAVY));
        left.addElement(blank(70));
        LineSeparator sigLineL = new LineSeparator(0.5f, 100f, LINE_LIGHT, Element.ALIGN_CENTER, 0);
        left.addElement(sigLineL);
        left.addElement(para("Authorised Signature & Seal", FS_MICRO, Font.ITALIC, TXT3, 0));
        table.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorderWidth(0.6f);
        right.setBorderColor(NAVY);
        right.setPaddingLeft(10);
        right.setPaddingRight(4);
        right.setPaddingTop(2);
        right.setPaddingBottom(4);
        right.addElement(para("F o r   &   o n   b e h a l f   o f   B U Y E R", FS_XS, Font.BOLD, GOLD, 2));
        right.addElement(boldParagraph(safe(buyer != null ? buyer.companyName() : ""), FS_BODY, 4, NAVY));
        right.addElement(blank(70));
        LineSeparator sigLineR = new LineSeparator(0.5f, 100f, LINE_LIGHT, Element.ALIGN_CENTER, 0);
        right.addElement(sigLineR);
        right.addElement(para("Authorised Signature & Seal", FS_MICRO, Font.ITALIC, TXT3, 0));
        table.addCell(right);

        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 11. 页脚：声明（三级灰斜体）+ 公司名（三级灰）+ 页码（深蓝加粗） */
    private void renderFooter(Document doc, ProformaDetails details) {
        try {
            Paragraph note = para(
                    "This is a computer-generated proforma invoice and does not require a physical signature unless otherwise stated.",
                    FS_MICRO, Font.ITALIC, TXT3, 0);
            note.setAlignment(Element.ALIGN_CENTER);
            doc.add(note);
            doc.add(new LineSeparator(0.5f, 100f, LINE_LIGHT, Element.ALIGN_CENTER, 0));
            Paragraph company = para(sellerCompanyName(details != null ? details.seller() : null),
                    FS_MICRO, Font.NORMAL, TXT3, 0);
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);

            Paragraph pageNum = para("Page " + doc.getPageNumber() + " of 1",
                    FS_MICRO, Font.BOLD, NAVY, 0);
            pageNum.setAlignment(Element.ALIGN_CENTER);
            doc.add(pageNum);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =====================================================================
    //  工具
    // =====================================================================

    private static List<QuoteDetailGroup> resolveDetailGroups(ProformaInvoice invoice) {
        Quotation q = invoice.getQuotation();
        if (q == null || q.getDetails() == null) return List.of();
        return q.getDetails();
    }

    private static int totalQty(List<QuoteDetailGroup> groups) {
        if (groups == null) return 0;
        return groups.stream()
                .filter(g -> g.items() != null)
                .flatMap(g -> g.items().stream())
                .mapToInt(QuoteDetailItem::quantity).sum();
    }

    private static String mainUnit(List<QuoteDetailGroup> groups) {
        if (groups == null) return "";
        return groups.stream()
                .filter(g -> g.items() != null && !g.items().isEmpty())
                .flatMap(g -> g.items().stream())
                .map(QuoteDetailItem::unit)
                .filter(u -> u != null && !u.isBlank())
                .findFirst().orElse("");
    }

    private static Map<String, String> parseTerms(String terms) {
        Map<String, String> map = new LinkedHashMap<>();
        if (terms == null || terms.isBlank()) return map;
        String[] labels = {"Country of Origin", "Port of Delivery", "Time of Delivery",
                "Payment Term", "Packing", "Note"};
        String[] lines = terms.split("\\r?\\n");
        java.util.List<String> extras = new java.util.ArrayList<>();
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

    private static Map<String, String> parseKvByFirstColon(String text) {
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

    private static String fmtMoney(BigDecimal v) {
        if (v == null) return "0";
        // 无小数时不显示 .00
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() <= 0) {
            return String.format("%,d", s.toBigInteger());
        }
        return String.format("%,.2f", v);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String sellerCompanyName(ProformaDetails.SellerInfo seller) {
        if (seller == null || seller.companyName() == null || seller.companyName().isBlank()) {
            return "";
        }
        return seller.companyName().trim();
    }

    private static String sellerChineseName(ProformaDetails.SellerInfo seller) {
        if (seller == null || seller.chineseName() == null || seller.chineseName().isBlank()) {
            return "";
        }
        return seller.chineseName().trim();
    }

    // ---- cell / paragraph 工厂（统一 textFont 选字 + 统一弱线色） ----

    private static PdfPCell cell(String text, float size, int style, Color color, Color bg,
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
        c.setBorderWidth(borderWidth * 1.2f);
        if (borderWidth > 0) {
            c.setBorderColor(NAVY);
        }
        return c;
    }

    /** 承载多色 Phrase 的单元格（如 DATE: 标签深蓝 + 值近黑；WARRANTY 金标签 + 灰值） */
    private static PdfPCell phraseCell(Phrase phrase, int hAlign, float padding, float borderWidth, Color bg) {
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
        c.setBorderWidth(borderWidth * 1.2f);
        if (borderWidth > 0) {
            c.setBorderColor(NAVY);
        }
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    /** 两色短语：label（深蓝加粗）+ value（近黑常规） */
    private static Phrase kvPhrase(String label, String value) {
        Phrase p = new Phrase();
        p.add(new Chunk(label, textFont(label, FS_REF, Font.BOLD, NAVY)));
        p.add(new Chunk(value, textFont(value, FS_REF, Font.NORMAL, TXT)));
        return p;
    }

    private static Paragraph para(String text, float size, int style, Color color, float spacingAfter) {
        Paragraph p = new Paragraph(safe(text), textFont(text, size, style, color));
        if (spacingAfter > 0) p.setSpacingAfter(spacingAfter);
        return p;
    }

    /** 金色小标签（S/B/WARRANTY 等区块标题） */
    private static Paragraph labelParagraph(String text) {
        return para(text, FS_S, Font.BOLD, GOLD, 2);
    }

    private static Paragraph boldParagraph(String text, float size, float spacingAfter, Color color) {
        Paragraph p = new Paragraph(safe(text), textFont(text, size, Font.BOLD, color));
        p.setLeading(0, 1.2f);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    /** Seller/Buyer 联系信息单行：标签深蓝加粗 + 值灰度常规（紧凑行距） */
    private static Paragraph kvLineParagraph(String label, String value) {
        Paragraph p = new Paragraph();
        p.setLeading(0, 1.2f);
        p.add(new Chunk(safe(label), textFont(label, FS_BODY, Font.BOLD, NAVY)));
        p.add(new Chunk(safe(value), textFont(value, FS_BODY, Font.NORMAL, TXT2)));
        p.add(new Chunk(" ", textFont(" ", FS_BODY, Font.NORMAL, TXT2)));
        p.setSpacingAfter(2);
        return p;
    }

    private static PdfPCell headerCell(String text) {
        return cell(text, FS_BODY, Font.BOLD, WHITE, NAVY, Element.ALIGN_CENTER, 6, 0.5f);
    }

    private static PdfPCell nameCell(String name, String hsCode, int rowspan) {
        Phrase ph = new Phrase();
        ph.add(new Chunk("Name: " + safe(name) + "\n", textFont("Name: " + safe(name), FS_MID, Font.BOLD | Font.ITALIC, NAVY)));
        ph.add(new Chunk("HS Code:\n", textFont("HS Code:", FS_MID, Font.BOLD | Font.ITALIC, NAVY)));
        ph.add(new Chunk(safe(hsCode), textFont(safe(hsCode), FS_XS, Font.NORMAL, TXT2)));
        PdfPCell c = phraseCell(ph, Element.ALIGN_LEFT, 6, 0.5f, WHITE);
        if (rowspan > 1) c.setRowspan(rowspan);
        return c;
    }

    private static PdfPCell detailCell(String desc) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(WHITE);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        c.setPaddingTop(4);
        c.setPaddingBottom(6);
        c.setBorderWidth(0.6f);
        c.setBorderColor(NAVY);

        String safeDesc = safe(desc);
        if (safeDesc.isEmpty()) {
            c.addElement(new Paragraph(" "));
            return c;
        }
        // 按换行拆成独立 Paragraph 逐个 addElement（OpenPDF 对含 \n 的单 Paragraph
        // setLeading 不生效，必须拆行后各自控制 leading + spacingAfter）
        String[] lines = safeDesc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Paragraph p = new Paragraph(line.isBlank() ? " " : line,
                    textFont(line, FS_BODY, Font.NORMAL, TXT2));
            p.setAlignment(Element.ALIGN_LEFT);
            p.setLeading(9f); // 行高（控制换行后的子行间距）
            if (i < lines.length - 1) {
                p.setSpacingAfter(2f); // 逻辑行之间的额外间距（description 行距）
            }
            c.addElement(p);
        }
        return c;
    }

    private static PdfPCell qtyCell(int qty, String unit) {
        Phrase ph = new Phrase();
        ph.add(new Chunk(String.valueOf(qty) + "\n", textFont(String.valueOf(qty), FS_QTY, Font.BOLD, NAVY)));
        ph.add(new Chunk(safe(unit), textFont(safe(unit), FS_MICRO, Font.NORMAL, TXT3)));
        return phraseCell(ph, Element.ALIGN_CENTER, 6, 0.5f, WHITE);
    }

    private static PdfPCell unitPriceCell(BigDecimal price, String unit, String ccy) {
        Phrase ph = new Phrase();
        ph.add(new Chunk((price != null ? fmtMoney(price) : "-") + "\n",
                textFont(price != null ? fmtMoney(price) : "-", FS_TITLE, Font.BOLD, NAVY)));
        ph.add(new Chunk(safe(ccy) + " / " + safe(unit), textFont(safe(ccy), FS_MICRO, Font.NORMAL, TXT3)));
        return phraseCell(ph, Element.ALIGN_CENTER, 6, 0.5f, WHITE);
    }

    private static PdfPCell totalPriceCell(BigDecimal total, String ccy) {
        Phrase ph = new Phrase();
        ph.add(new Chunk((total != null ? fmtMoney(total) : "-") + "\n",
                textFont(total != null ? fmtMoney(total) : "-", FS_TITLE, Font.BOLD, NAVY)));
        ph.add(new Chunk(safe(ccy), textFont(safe(ccy), FS_MICRO, Font.NORMAL, TXT3)));
        return phraseCell(ph, Element.ALIGN_CENTER, 6, 0.5f, WHITE);
    }
}
