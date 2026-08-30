package com.yunhe.website.crm.support;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
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
 * <p>继承 {@link AbstractTradePdfRenderer} 复用字体基础设施与所有底层 cell/phrase 工厂；本类只负责：
 * <ul>
 *   <li>PI 专属调色板（NAVY 主色 / GOLD 强调 / TXT* 灰度 / ZONE·CARD 浅底 / LIGHT_BLUE 抬头中文），
 *       并以 {@link #borderColor()} 覆写为 NAVY（基类默认黑，CI 保持黑）。</li>
 *   <li>PI 版面：公司头部深蓝带 / 金色大标题 / DATE·REF / SELLER·BUYER / ITEM 表 / TOTAL / TERMS / BANK / 签名。</li>
 * </ul>
 *
 * <p>字体规范：英文只用 4 种 Carlito（SIL OFL 开源，Calibri 等距克隆，靠字体名加粗/斜体，不靠 weight 位）；
 * 中文用 Noto Sans SC 子集（glyf/TrueType，GB2312 常用字），由基类 {@link #textFont} 按是否含 CJK 自动切换。</p>
 */
@Slf4j
@Component
public class PiPdfRenderer extends AbstractTradePdfRenderer {

    // ===================== PI 专属色板（与原模板核对） =====================
    private static final Color NAVY = new Color(26, 60, 94);       // #1A3C5E 主文字/标题/深蓝带
    private static final Color GOLD = new Color(201, 162, 39);     // #C9A227 大标题背景/强调标签/唯一 TOTAL 区
    private static final Color TXT = new Color(26, 26, 26);        // #1A1A1A 近黑正文
    private static final Color TXT2 = new Color(68, 68, 68);       // #444444 灰度主体
    private static final Color TXT3 = new Color(102, 102, 102);   // #666666 三级灰
    private static final Color LINE_LIGHT = new Color(138, 164, 190); // 页脚/签名线浅蓝灰
    private static final Color ZONE = new Color(234, 241, 248);    // #EAF1F8 TERMS 标签列底
    private static final Color CARD = new Color(245, 248, 252);    // #F5F8FC 浅底
    private static final Color LIGHT_BLUE = new Color(184, 196, 208); // #B8C4D0 抬头中文名（反白于深蓝带）

    // ===================== PI 专属布局 =====================
    private static final float SIGNATURE_GAP = 80f;     // 签名区公司名与签名线之间的留白
    private static final float[] ITEM_COL_WIDTHS = {1.1f, 4.5f, 1f, 1.4f, 1.6f}; // 货物表/金额表列宽
    private static final String PI_DECLARATION =
            "This is a computer-generated proforma invoice and does not require a physical signature unless otherwise stated.";

    @Override
    protected Color borderColor() {
        return NAVY;
    }

    // =====================================================================
    //  主流程
    // =====================================================================

    public byte[] render(ProformaInvoice invoice) {
        ProformaDetails details = invoice.getDetails();
        ProformaDetails.BuyerInfo buyer = details != null ? details.buyer() : null;

        Document doc = new Document(PageSize.A4, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterEvent(sellerCompanyName(details != null ? details.seller() : null),
                    PI_DECLARATION, TXT3, LINE_LIGHT));
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
        companyName = companyName.toUpperCase();

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell companyNameCell = cell(companyName, FS_TITLE, Font.BOLD, WHITE, NAVY, Element.ALIGN_LEFT, PAD, 0);
        table.addCell(companyNameCell);
        String chineseName = sellerChineseName(details != null ? details.seller() : null);
        if (!chineseName.isEmpty()) {
            PdfPCell chineseNameCell = cell(chineseName, FS_BODY, Font.ITALIC, LIGHT_BLUE, NAVY, Element.ALIGN_LEFT, PAD, 0);
            table.addCell(chineseNameCell);
        }
        add(doc, table);
    }

    /** 2. 大标题：PROFORMA INVOICE，金色背景 + 深蓝加粗字 */
    private void renderTitle(Document doc) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell c = cell("P R O F O R M A   I N V O I C E",
                FS_H1, Font.BOLD, NAVY, GOLD, Element.ALIGN_CENTER, PAD, BORDER_WIDTH);
        c.setBorder(0);
        c.setLeading(0, LEADING);
        table.addCell(c);
        add(doc, table);
    }

    /** 3. DATE / REF. No. 双列（标签深蓝加粗 + 值近黑常规，均 9.5pt） */
    private void renderDateRef(Document doc, ProformaInvoice invoice) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        Phrase datePhrase = kvPhrase("DATE:  ", invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : "",
                Font.BOLD, NAVY, TXT);
        Phrase refPhrase = kvPhrase("REF. No.:  ", safe(invoice.getInvoiceNumber()),
                Font.BOLD, NAVY, TXT);
        table.addCell(phraseCell(datePhrase, Element.ALIGN_LEFT, PAD, BORDER_WIDTH, WHITE));
        table.addCell(phraseCell(refPhrase, Element.ALIGN_RIGHT, PAD, BORDER_WIDTH, WHITE));
        add(doc, table);
    }

    /** 4. SELLER / BUYER 双列（浅底卡片 + 弱边框；标签金色；公司名深蓝加粗；联系值标签深蓝加粗+值灰度） */
    private void renderSellerBuyer(Document doc, ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        ProformaDetails.SellerInfo seller = details != null ? details.seller() : null;

        // 左：SELLER
        PdfPCell sellerCell = borderedCardCell();
        sellerCell.addElement(labelParagraph("S E L L E R"));
        if (seller != null) {
            if (seller.companyName() != null && !seller.companyName().isBlank()) {
                sellerCell.addElement(boldParagraph(seller.companyName(), FS_BODY, 2, NAVY));
            }
            if (seller.address() != null && !seller.address().isBlank())
                sellerCell.addElement(kvLineParagraph("Add: ", seller.address(), Font.BOLD, NAVY, TXT2));
            if (seller.phone() != null && !seller.phone().isBlank())
                sellerCell.addElement(kvLineParagraph("Tel: ", seller.phone(), Font.BOLD, NAVY, TXT2));
            if (seller.email() != null && !seller.email().isBlank())
                sellerCell.addElement(kvLineParagraph("Email: ", seller.email(), Font.BOLD, NAVY, TXT2));
        }
        table.addCell(sellerCell);

        // 右：BUYER
        PdfPCell buyerCell = borderedCardCell();
        buyerCell.addElement(labelParagraph("B U Y E R"));
        if (buyer != null) {
            buyerCell.addElement(boldParagraph(safe(buyer.companyName()), FS_BODY, 2, NAVY));
            if (buyer.registrationNo() != null && !buyer.registrationNo().isBlank())
                buyerCell.addElement(kvLineParagraph("Registration No.: ", buyer.registrationNo(), Font.BOLD, NAVY, TXT2));
            if (buyer.address() != null && !buyer.address().isBlank())
                buyerCell.addElement(kvLineParagraph("Add: ", buyer.address(), Font.BOLD, NAVY, TXT2));
        }
        table.addCell(buyerCell);

        add(doc, table);
    }

    /** 5. ITEM 表格：深蓝表头白字（8pt），浅底表体；Name 加粗斜体深蓝；描述灰度；数量13pt；金额11pt */
    private void renderItemTable(Document doc, ProformaInvoice invoice) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(ITEM_COL_WIDTHS);

        table.addCell(headerCell("I T E M", NAVY, WHITE, FS_LABEL));
        table.addCell(headerCell("C O M M O D I T Y  N A M E", NAVY, WHITE, FS_LABEL));
        table.addCell(headerCell("Q T Y", NAVY, WHITE, FS_LABEL));
        table.addCell(headerCell("U N I T  P R I C E", NAVY, WHITE, FS_LABEL));
        table.addCell(headerCell("T O T A L  P R I C E", NAVY, WHITE, FS_LABEL));

        List<QuoteDetailGroup> groups = resolveGroups(invoice.getQuotation());
        if (groups == null || groups.isEmpty()) {
            table.addCell(cell("—", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, PAD, BORDER_WIDTH));
            table.addCell(cell("(无明细)", FS_LABEL, Font.ITALIC, TXT3, WHITE, Element.ALIGN_LEFT, PAD, BORDER_WIDTH));
            table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, PAD, BORDER_WIDTH));
            table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, PAD, BORDER_WIDTH));
            table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, PAD, BORDER_WIDTH));
        } else {
            for (QuoteDetailGroup g : groups) {
                List<QuoteDetailItem> items = g.items() != null ? g.items() : List.of();
                int groupSpan = Math.max(1, items.size());
                int i = 0;
                for (QuoteDetailItem it : items) {
                    if (i == 0) {
                        table.addCell(nameCell(g.name(), g.hsCode(), groupSpan, NAVY, TXT2));
                    }
                    table.addCell(detailCell(g.name(), it.description(), NAVY, TXT2));
                    table.addCell(qtyCell(it.quantity(), it.unit(), NAVY, TXT3));
                    table.addCell(unitPriceCell(it.unitPrice(), it.unit(), it.currency(), NAVY, TXT3));
                    table.addCell(totalPriceCell(
                            it.unitPrice() != null && it.quantity() > 0
                                    ? it.unitPrice().multiply(BigDecimal.valueOf(it.quantity())) : null,
                            it.currency(), NAVY, TXT3));
                    i++;
                }
                if (items.isEmpty()) {
                    table.addCell(nameCell(g.name(), g.hsCode(), 1, NAVY, TXT2));
                    table.addCell(cell("—", FS_LABEL, Font.NORMAL, TXT3, WHITE, Element.ALIGN_LEFT, PAD, BORDER_WIDTH));
                    table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, PAD, BORDER_WIDTH));
                    table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, PAD, BORDER_WIDTH));
                    table.addCell(cell("", FS_BODY, Font.NORMAL, TXT3, WHITE, Element.ALIGN_CENTER, PAD, BORDER_WIDTH));
                }
            }
        }
        add(doc, table);
    }

    /** 6. TOTAL：深蓝标签带 + 单一金色金额区（11pt 深蓝）+ 数量（白底深蓝）+ 可选 WARRANTY 整行 */
    private void renderTotal(Document doc, ProformaInvoice invoice, ProformaDetails details) {
        Map<String, BigDecimal> totalByCcy = new LinkedHashMap<>();
        List<QuoteDetailGroup> groups = resolveGroups(invoice.getQuotation());
        if (groups != null) {
            for (QuoteDetailGroup g : groups) {
                if (g.items() == null) continue;
                for (QuoteDetailItem it : g.items()) {
                    if (it.unitPrice() == null || it.quantity() <= 0) continue;
                    String ccy = it.currency() != null ? it.currency() : DEFAULT_CCY;
                    BigDecimal lineTotal = it.unitPrice().multiply(BigDecimal.valueOf(it.quantity()));
                    totalByCcy.merge(ccy, lineTotal, BigDecimal::add);
                }
            }
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(ITEM_COL_WIDTHS);
        String mainCcy = totalByCcy.keySet().stream().findFirst().orElse(DEFAULT_CCY);
        BigDecimal mainTotal = totalByCcy.getOrDefault(mainCcy, BigDecimal.ZERO);

        PdfPCell label = cell("T O T A L   —   F O B   C H I N A   P O R T",
                FS_BODY, Font.BOLD, WHITE, NAVY, Element.ALIGN_CENTER, PAD, BORDER_WIDTH);
        label.setColspan(4);
        table.addCell(label);
        Phrase totalPh = new Phrase();
        totalPh.add(new Chunk(fmtMoney(mainTotal) + " " + mainCcy + "\n",
                textFont(fmtMoney(mainTotal) + mainCcy, FS_BODY, Font.BOLD, NAVY)));
        totalPh.add(new Chunk(totalQty(groups) + " " + mainUnit(groups),
                textFont(mainUnit(groups), FS_MICRO, Font.NORMAL, TXT3)));
        PdfPCell totalCell = phraseCell(totalPh, Element.ALIGN_CENTER, PAD, BORDER_WIDTH, GOLD);
        totalCell.setColspan(1);
        table.addCell(totalCell);

        // WARRANTY 直接合并进 TOTAL 表（整行，金标签 + 灰值，弱边框白底）
        String warranty = details != null ? details.warranty() : null;
        if (warranty != null && !warranty.isBlank()) {
            Phrase wPh = new Phrase();
            wPh.add(new Chunk("W A R R A N T Y    ", textFont("W A R R A N T Y", FS_LABEL, Font.BOLD, GOLD)));
            wPh.add(new Chunk(safe(warranty), textFont(warranty, FS_BODY, Font.NORMAL, TXT2)));
            PdfPCell wCell = phraseCell(wPh, Element.ALIGN_LEFT, PAD, BORDER_WIDTH, WHITE);
            wCell.setColspan(5);
            table.addCell(wCell);
        }

        add(doc, table);
    }

    /** 7. TERMS & CONDITIONS：深蓝文字标题（无表格框，下方金色线）+ 标签列浅蓝底 + 值灰度 */
    private void renderTermsAndConditions(Document doc, ProformaDetails details) {
        String title = "T E R M S   &   C O N D I T I O N S";
        Map<String, String> rows = parseTerms(details != null ? details.terms() : null);

        doc.add(para(title, FS_H2, Font.BOLD, NAVY, 3));
        doc.add(goldLine());
        doc.add(blank(4));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        String[] labels = {"Country of Origin", "Port of Delivery", "Time of Delivery",
                "Payment Term", "Packing", "Note"};
        for (String label : labels) {
            table.addCell(cell(label, FS_LABEL, Font.BOLD, NAVY, ZONE, Element.ALIGN_LEFT, PAD, BORDER_WIDTH));
            table.addCell(cell(safe(rows.get(label)), FS_BODY, Font.NORMAL, TXT2, WHITE, Element.ALIGN_LEFT, PAD, BORDER_WIDTH));
        }
        if (rows.containsKey("_extra")) {
            PdfPCell extra = cell(rows.get("_extra"), FS_BODY, Font.ITALIC, TXT2, CARD, Element.ALIGN_LEFT, PAD, BORDER_WIDTH);
            extra.setColspan(2);
            table.addCell(extra);
        }
        add(doc, table);
    }

    /** 8. BANK ACCOUNT：深蓝文字标题（无表格框，下方金色线）+ 标签行深蓝带/白底交替，值灰度 */
    private void renderBankAccount(Document doc, ProformaDetails details) {
        String title = "B A N K   A C C O U N T   I N F O R M A T I O N";
        doc.add(para(title, FS_H2, Font.BOLD, NAVY, 3));
        doc.add(goldLine());
        doc.add(blank(4));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        String[] labels = {"Beneficiary Name", "Beneficiary Address", "Bank Name", "Bank Address", "Account No.",
                "SWIFT Code"};
        Map<String, String> kv = parseKvByFirstColon(details != null ? details.bankAccountInformation() : null);
        for (String label : labels) {
            table.addCell(cell(label, FS_LABEL, Font.BOLD, NAVY, ZONE, Element.ALIGN_LEFT, PAD, BORDER_WIDTH));
            table.addCell(cell(safe(kv.get(label)), FS_BODY, Font.NORMAL, TXT2, WHITE, Element.ALIGN_LEFT, PAD, BORDER_WIDTH));
        }
        add(doc, table);
        doc.add(blank(4));
    }

    /** 9. 签名区：金色 "F o r..." 标签 + 深蓝公司名 + 斜体说明（浅蓝外框） */
    private void renderSignatures(Document doc, ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        PdfPCell left = borderedCardCell();
        left.addElement(labelParagraph("F o r   &   o n   b e h a l f   o f   S E L L E R"));
        left.addElement(boldParagraph(sellerCompanyName(details != null ? details.seller() : null), FS_BODY, 4, NAVY));
        left.addElement(blank(SIGNATURE_GAP));
        LineSeparator sigLineL = new LineSeparator(0.5f, 100f, LINE_LIGHT, Element.ALIGN_CENTER, 0);
        left.addElement(sigLineL);
        left.addElement(para("Authorised Signature & Seal", FS_MICRO, Font.ITALIC, TXT3, 0));
        table.addCell(left);

        PdfPCell right = borderedCardCell();
        right.addElement(labelParagraph("F o r   &   o n   b e h a l f   o f   B U Y E R"));
        right.addElement(boldParagraph(safe(buyer != null ? buyer.companyName() : ""), FS_BODY, 4, NAVY));
        right.addElement(blank(SIGNATURE_GAP));
        LineSeparator sigLineR = new LineSeparator(0.5f, 100f, LINE_LIGHT, Element.ALIGN_CENTER, 0);
        right.addElement(sigLineR);
        right.addElement(para("Authorised Signature & Seal", FS_MICRO, Font.ITALIC, TXT3, 0));
        table.addCell(right);

        add(doc, table);
    }

    // =====================================================================
    //  PI 专属小部件
    // =====================================================================

    /** 标题下方的金色细线（实心金色条，更细，无表格框） */
    private static PdfPTable goldLine() {
        PdfPTable sep = new PdfPTable(1);
        sep.setWidthPercentage(100);
        PdfPCell s = new PdfPCell(new Phrase(" ", textFont(" ", FS_MICRO, Font.NORMAL, TXT3)));
        s.setBackgroundColor(GOLD);
        s.setBorder(0);
        s.setFixedHeight(0.8f);
        sep.addCell(s);
        return sep;
    }

    /** 金色小标签（S/B/WARRANTY 等区块标题） */
    private static Paragraph labelParagraph(String text) {
        Paragraph p = para(text, FS_LABEL, Font.BOLD, GOLD, 2);
        p.setLeading(0f, LEADING); // 与全局统一行距（1.2×）一致，区块标签不再单独钉顶
        return p;
    }
}
