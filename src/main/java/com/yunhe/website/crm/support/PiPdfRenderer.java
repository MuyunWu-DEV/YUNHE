package com.yunhe.website.crm.support;

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
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.ProformaInvoice;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Proforma Invoice PDF 渲染器：按参考模板「整体相同 + 合理优化」渲染（彩色版）。
 * <p>布局自上而下：公司头 → 大标题 → 日期/编号 → SELLER/BUYER → ITEM 表格 → TOTAL →
 * WARRANTY → TERMS & CONDITIONS → BANK ACCOUNT → 签名 → 页脚。</p>
 * <p>配色：参照模板采用深海军蓝 #193B5E（公司头/表头/标题/TOTAL 标签）、金黄 #C9A126
 * （大标题/金额高亮）、浅蓝白 #F5F8FC（次级背景）。</p>
 */
@Component
@Slf4j
public class PiPdfRenderer {

    /** 深海军蓝 #193B5E（公司头、表头、标题、TOTAL 标签） */
    private static final Color DARK = new Color(25, 59, 94);
    /** 金黄 #C9A126（大标题、TOTAL 金额高亮） */
    private static final Color LIGHT = new Color(201, 161, 38);
    /** 浅蓝白 #F5F8FC（次级背景/分隔） */
    private static final Color LIGHTER = new Color(245, 248, 252);

    /** 中文字体（按优先级从 classpath fonts/ 加载：NotoSansSC / SimHei / SimSun / iText-asian STSong-Light；失败降级为默认） */
    private static final BaseFont CHINESE_FONT = initChineseFont();

    private static BaseFont initChineseFont() {
        // 1. 优先从 classpath 加载开源中文字体（推荐 Noto Sans CJK SC，放到 src/main/resources/fonts/）
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String[] fontResources = {
                "fonts/NotoSansSC-Regular.otf",
                "fonts/NotoSansSC-Regular.ttf",
                "fonts/simhei.ttf",
                "fonts/simsun.ttc"
        };
        for (String resource : fontResources) {
            try (InputStream is = cl.getResourceAsStream(resource)) {
                if (is == null) continue;
                byte[] data = is.readAllBytes();
                BaseFont bf = BaseFont.createFont(resource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, data, null);
                log.info("已加载中文字体：{}", resource);
                return bf;
            } catch (Exception e) {
                log.debug("字体 {} 加载失败", resource, e);
            }
        }
        // 2. 回退：尝试 iText-asian 内置 STSong-Light（若日后引入依赖）
        try {
            return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            log.debug("iText-asian STSong-Light 不可用");
        }
        log.warn("未找到中文字体，PDF 中文将无法渲染（请将字体文件放入 src/main/resources/fonts/）");
        return null;
    }

    /** 创建 Font（中文时使用中文字体，否则用 Helvetica；失败时回退） */
    private static Font f(float size, int style, Color color) {
        if (CHINESE_FONT != null) {
            return new Font(CHINESE_FONT, size, style, color);
        }
        return new Font(Font.HELVETICA, size, style, color);
    }

    private static Paragraph blank(float pt) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(pt);
        return p;
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
            renderTotal(doc, invoice);
            doc.add(blank(8));
            renderWarranty(doc, details);
            doc.add(blank(8));
            renderTermsAndConditions(doc, details);
            doc.add(blank(8));
            renderBankAccount(doc, details);
            doc.add(blank(16));
            renderSignatures(doc, details, buyer);
            renderFooter(doc, details);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成 PI PDF 失败：" + e.getMessage(), e);
        }
    }

    // =====================================================================
    //  各段落
    // =====================================================================

    /** 1. 公司头部：深灰底白字（公司英文名 + 中文名/副名），下方浅灰分隔线 */
    private void renderCompanyHeader(Document doc, ProformaDetails details) {
        String companyName = sellerCompanyName(details != null ? details.seller() : null);
        if (companyName.isEmpty()) {
            companyName = "SELLER";
        }

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.addCell(cell(companyName, f(14, Font.BOLD, Color.WHITE), DARK, Element.ALIGN_CENTER, 8, 0));
        table.addCell(cell(companyName, f(9, Font.NORMAL, Color.WHITE), DARK, Element.ALIGN_CENTER, 6, 0));
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        PdfPTable sep = new PdfPTable(1);
        sep.setWidthPercentage(100);
        PdfPCell s = new PdfPCell(new Phrase(" "));
        s.setBackgroundColor(LIGHT);
        s.setFixedHeight(2.5f);
        s.setBorder(0);
        sep.addCell(s);
        try {
            doc.add(sep);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 2. 大标题：PROFORMA INVOICE，浅灰底深灰字 */
    private void renderTitle(Document doc) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell c = cell("P R O F O R M A   I N V O I C E",
                f(20, Font.BOLD, DARK), LIGHT, Element.ALIGN_CENTER, 14, 0);
        table.addCell(c);
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 3. DATE / REF. No. 双列 */
    private void renderDateRef(Document doc, ProformaInvoice invoice) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});
        table.addCell(cell("DATE:  " + (invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : ""),
                f(10, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_LEFT, 6, 1));
        table.addCell(cell("REF. No.:  " + safe(invoice.getInvoiceNumber()),
                f(10, Font.BOLD, Color.BLACK), Color.WHITE, Element.ALIGN_RIGHT, 6, 1));
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 4. SELLER / BUYER 双列 */
    private void renderSellerBuyer(Document doc, ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        // 左：SELLER
        PdfPCell sellerCell = new PdfPCell();
        sellerCell.setBorderWidth(1);
        sellerCell.setBorderColor(LIGHT);
        sellerCell.setPadding(0);
        sellerCell.addElement(labelParagraph("S E L L E R"));
        sellerCell.addElement(multiLineParagraph(sellerText(details != null ? details.seller() : null), 8, 4));
        table.addCell(sellerCell);

        // 右：BUYER
        PdfPCell buyerCell = new PdfPCell();
        buyerCell.setBorderWidth(1);
        buyerCell.setBorderColor(LIGHT);
        buyerCell.setPadding(0);
        buyerCell.addElement(labelParagraph("B U Y E R"));
        if (buyer != null) {
            buyerCell.addElement(boldParagraph(safe(buyer.companyName()), 9, 4));
            buyerCell.addElement(multiLineParagraph(
                    "Registration No.: " + safe(buyer.registrationNo()) + "\n"
                            + "Add: " + safe(buyer.address()), 8, 4));
        } else {
            buyerCell.addElement(multiLineParagraph("", 8, 4));
        }
        table.addCell(buyerCell);

        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 5. ITEM 表格：深灰表头，多行（每个商品一行） */
    private void renderItemTable(Document doc, ProformaInvoice invoice) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.1f, 4.5f, 1f, 1.4f, 1.6f});

        // 表头
        table.addCell(headerCell("ITEM"));
        table.addCell(headerCell("C O M M O D I T Y  N A M E"));
        table.addCell(headerCell("Q T Y"));
        table.addCell(headerCell("U N I T  P R I C E"));
        table.addCell(headerCell("T O T A L  P R I C E"));

        // 明细行
        List<QuoteDetailGroup> groups = resolveDetailGroups(invoice);
        if (groups == null || groups.isEmpty()) {
            table.addCell(cell("—", f(8, Font.NORMAL, Color.BLACK), LIGHTER, Element.ALIGN_CENTER, 6, 1));
            table.addCell(cell("(无明细)", f(8, Font.ITALIC, Color.GRAY), LIGHTER, Element.ALIGN_LEFT, 6, 1));
            table.addCell(cell("", f(8, Font.NORMAL, Color.BLACK), LIGHTER, Element.ALIGN_CENTER, 6, 1));
            table.addCell(cell("", f(8, Font.NORMAL, Color.BLACK), LIGHTER, Element.ALIGN_CENTER, 6, 1));
            table.addCell(cell("", f(8, Font.NORMAL, Color.BLACK), LIGHTER, Element.ALIGN_CENTER, 6, 1));
        } else {
            for (QuoteDetailGroup g : groups) {
                List<QuoteDetailItem> items = g.items() != null ? g.items() : List.of();
                int groupSpan = Math.max(1, items.size());
                String nameLine = "Name: " + safe(g.name());
                if (g.hsCode() != null && !g.hsCode().isBlank()) {
                    nameLine += "\nHS Code:\n" + safe(g.hsCode());
                }
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
                    table.addCell(cell("—", f(8, Font.NORMAL, Color.GRAY), Color.WHITE, Element.ALIGN_LEFT, 6, 1));
                    table.addCell(cell("", f(8, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_CENTER, 6, 1));
                    table.addCell(cell("", f(8, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_CENTER, 6, 1));
                    table.addCell(cell("", f(8, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_CENTER, 6, 1));
                }
            }
        }
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 6. TOTAL：深灰底「TOTAL — FOB ...」+ 浅灰底金额 + 数量 */
    private void renderTotal(Document doc, ProformaInvoice invoice) {
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
        // 取第一个币种做主显示（模板只一种）
        String mainCcy = totalByCcy.keySet().stream().findFirst().orElse("USD");
        BigDecimal mainTotal = totalByCcy.getOrDefault(mainCcy, BigDecimal.ZERO);

        PdfPCell label = cell("T O T A L   —   F O B   C H I N A   P O R T",
                f(11, Font.BOLD, Color.WHITE), DARK, Element.ALIGN_CENTER, 10, 0);
        label.setColspan(3);
        table.addCell(label);
        table.addCell(cell(fmtMoney(mainTotal) + " " + mainCcy,
                f(13, Font.BOLD, DARK), LIGHT, Element.ALIGN_RIGHT, 10, 1));
        table.addCell(cell(totalQty(groups) + "  " + mainUnit(groups),
                f(9, Font.NORMAL, DARK), LIGHT, Element.ALIGN_RIGHT, 10, 1));
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 7. WARRANTY：浅灰标签 + 单行内容 */
    private void renderWarranty(Document doc, ProformaDetails details) {
        String warranty = details != null ? details.warranty() : null;
        if (warranty == null || warranty.isBlank()) {
            return; // 老数据无 warranty 时不显示该行
        }
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell c = cell("WARRANTY  " + warranty,
                f(9, Font.NORMAL, Color.BLACK), LIGHTER, Element.ALIGN_LEFT, 8, 1);
        table.addCell(c);
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 8. TERMS & CONDITIONS：标签 + 多行内容（terms 按行拆分到 6 个固定字段） */
    private void renderTermsAndConditions(Document doc, ProformaDetails details) {
        String title = "T E R M S   &   C O N D I T I O N S";
        Map<String, String> rows = parseTerms(details != null ? details.terms() : null);

        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.addCell(cell(title, f(11, Font.BOLD, Color.WHITE), DARK, Element.ALIGN_LEFT, 8, 0));
        try {
            doc.add(header);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        String[] labels = {"Country of Origin", "Port of Delivery", "Time of Delivery",
                "Payment Term", "Packing", "Note"};
        for (String label : labels) {
            table.addCell(cell(label, f(9, Font.BOLD, Color.BLACK), LIGHTER, Element.ALIGN_LEFT, 6, 1));
            table.addCell(cell(safe(rows.get(label)), f(9, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_LEFT, 6, 1));
        }
        // 额外内容（超出 6 行的）
        if (rows.containsKey("_extra")) {
            PdfPCell extra = cell(rows.get("_extra"), f(9, Font.ITALIC, Color.DARK_GRAY),
                    LIGHTER, Element.ALIGN_LEFT, 6, 1);
            extra.setColspan(2);
            table.addCell(extra);
        }
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 9. BANK ACCOUNT INFORMATION：标签 + 多行内容（按 \n 拆分） */
    private void renderBankAccount(Document doc, ProformaDetails details) {
        String title = "B A N K   A C C O U N T   I N F O R M A T I O N";
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        header.addCell(cell(title, f(11, Font.BOLD, Color.WHITE), DARK, Element.ALIGN_LEFT, 8, 0));
        try {
            doc.add(header);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        String[] labels = {"Bank Name", "Bank Address", "SWIFT Code", "Account No.",
                "Beneficiary Name", "Beneficiary Address"};
        Map<String, String> kv = parseKvByFirstColon(details != null ? details.bankAccountInformation() : null);
        for (String label : labels) {
            table.addCell(cell(label, f(9, Font.BOLD, Color.BLACK), LIGHTER, Element.ALIGN_LEFT, 6, 1));
            table.addCell(cell(safe(kv.get(label)), f(9, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_LEFT, 6, 1));
        }
        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 10. 签名区：双列 For & on behalf of SELLER / BUYER + 签名线 */
    private void renderSignatures(Document doc, ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        // 左：SELLER
        PdfPCell left = new PdfPCell();
        left.setBorder(0);
        left.setPadding(4);
        Paragraph lLabel = new Paragraph("F o r   &   o n   b e h a l f   o f   S E L L E R",
                f(9, Font.BOLD, DARK));
        lLabel.setSpacingAfter(2);
        left.addElement(lLabel);
        String sellerName = sellerCompanyName(details != null ? details.seller() : null);
        left.addElement(boldParagraph(safe(sellerName), 9, 4));
        left.addElement(blank(20));
        left.addElement(new Paragraph("Authorised Signature & Seal",
                f(8, Font.ITALIC, Color.DARK_GRAY)));
        table.addCell(left);

        // 右：BUYER
        PdfPCell right = new PdfPCell();
        right.setBorder(0);
        right.setPadding(4);
        Paragraph rLabel = new Paragraph("F o r   &   o n   b e h a l f   o f   B U Y E R",
                f(9, Font.BOLD, DARK));
        rLabel.setSpacingAfter(2);
        right.addElement(rLabel);
        right.addElement(boldParagraph(safe(buyer != null ? buyer.companyName() : ""), 9, 4));
        right.addElement(blank(20));
        right.addElement(new Paragraph("Authorised Signature & Seal",
                f(8, Font.ITALIC, Color.DARK_GRAY)));
        table.addCell(right);

        try {
            doc.add(table);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 11. 页脚：声明 + 公司名 + 页码 */
    private void renderFooter(Document doc, ProformaDetails details) {
        try {
            Paragraph note = new Paragraph(
                    "This is a computer-generated proforma invoice and does not require a physical signature unless otherwise stated.",
                    f(8, Font.ITALIC, Color.DARK_GRAY));
            note.setAlignment(Element.ALIGN_CENTER);
            note.setSpacingBefore(12);
            doc.add(note);

            Paragraph company = new Paragraph(sellerCompanyName(details != null ? details.seller() : null),
                    f(8, Font.NORMAL, Color.DARK_GRAY));
            company.setAlignment(Element.ALIGN_CENTER);
            doc.add(company);
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

    /** 解析 terms：识别 Country of Origin / Port of Delivery 等固定行；额外行放 _extra */
    private static Map<String, String> parseTerms(String terms) {
        Map<String, String> map = new LinkedHashMap<>();
        if (terms == null || terms.isBlank()) return map;
        String[] labels = {"Country of Origin", "Port of Delivery", "Time of Delivery",
                "Payment Term", "Packing", "Note"};
        String[] lines = terms.split("\\r?\\n");
        List<String> extras = new java.util.ArrayList<>();
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

    /** 解析 bank 文本为 kv（按首个 ":" 分割） */
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
        return String.format("%,.2f", v);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Seller 公司名（空则返回 ""） */
    private static String sellerCompanyName(ProformaDetails.SellerInfo seller) {
        if (seller == null || seller.companyName() == null || seller.companyName().isBlank()) {
            return "";
        }
        return seller.companyName().trim();
    }

    /** Seller 完整信息（公司名/地址/电话/邮箱拼成多行文本） */
    private static String sellerText(ProformaDetails.SellerInfo seller) {
        if (seller == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (seller.companyName() != null && !seller.companyName().isBlank()) {
            sb.append(seller.companyName()).append('\n');
        }
        if (seller.address() != null && !seller.address().isBlank()) {
            sb.append("Add: ").append(seller.address()).append('\n');
        }
        if (seller.phone() != null && !seller.phone().isBlank()) {
            sb.append("Tel: ").append(seller.phone()).append('\n');
        }
        if (seller.email() != null && !seller.email().isBlank()) {
            sb.append("Email: ").append(seller.email());
        }
        return sb.toString().trim();
    }

    // ---- cell 工厂 ----

    private static PdfPCell cell(String text, Font font, Color bg, int hAlign, float padding, float borderWidth) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(hAlign);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(padding);
        c.setBorderWidth(borderWidth);
        return c;
    }

    /**
     * 段落式浅灰标签：返回 Paragraph（带 LIGHT 背景），用于 PdfPCell 内部作为子元素
     * （PdfPCell.addElement 不能直接接受 PdfPCell，会抛 "Element not allowed"）。
     */
    private Paragraph labelParagraph(String text) {
        Paragraph p = new Paragraph(text, f(9, Font.BOLD, DARK));
        return p;
    }

    private Paragraph boldParagraph(String text, float size, float spacingAfter) {
        Paragraph p = new Paragraph(text, f(size, Font.BOLD, Color.BLACK));
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private Paragraph multiLineParagraph(String text, float size, float spacingAfter) {
        Paragraph p = new Paragraph();
        if (text != null) {
            for (String line : text.split("\\r?\\n")) {
                p.add(new Phrase(line + "\n", f(size, Font.NORMAL, Color.BLACK)));
            }
        }
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private PdfPCell headerCell(String text) {
        return cell(text, f(9, Font.BOLD, Color.WHITE), DARK, Element.ALIGN_CENTER, 6, 1);
    }

    private PdfPCell nameCell(String name, String hsCode, int rowspan) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(Color.WHITE);
        c.setPadding(6);
        c.setBorderWidth(1);
        Paragraph n = new Paragraph("Name: " + safe(name), f(8, Font.BOLD, Color.BLACK));
        c.addElement(n);
        if (hsCode != null && !hsCode.isBlank()) {
            Paragraph h = new Paragraph("HS Code:\n" + safe(hsCode), f(8, Font.NORMAL, Color.BLACK));
            c.addElement(h);
        }
        if (rowspan > 1) c.setRowspan(rowspan);
        return c;
    }

    private PdfPCell detailCell(String desc) {
        return cell(safe(desc), f(8, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_LEFT, 6, 1);
    }

    private PdfPCell qtyCell(int qty, String unit) {
        return cell(qty + "\n" + safe(unit), f(10, Font.BOLD, Color.BLACK), Color.WHITE, Element.ALIGN_CENTER, 6, 1);
    }

    private PdfPCell unitPriceCell(BigDecimal price, String unit, String ccy) {
        String body = (price != null ? fmtMoney(price) : "-")
                + "\n" + safe(ccy) + " / " + safe(unit);
        return cell(body, f(9, Font.NORMAL, Color.BLACK), Color.WHITE, Element.ALIGN_CENTER, 6, 1);
    }

    private PdfPCell totalPriceCell(BigDecimal total, String ccy) {
        String body = (total != null ? fmtMoney(total) : "-") + "\n" + safe(ccy);
        return cell(body, f(10, Font.BOLD, Color.BLACK), LIGHTER, Element.ALIGN_CENTER, 6, 1);
    }
}
