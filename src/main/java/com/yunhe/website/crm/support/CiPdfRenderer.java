package com.yunhe.website.crm.support;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.yunhe.website.crm.entity.CommercialInvoice;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Commercial Invoice PDF 渲染器（严格对齐参考单据 Commercial Invoice 的实测版面框架）。
 *
 * <p>继承 {@link AbstractTradePdfRenderer} 复用字体基础设施与所有底层 cell/phrase 工厂；本类只负责：
 * <ul>
 *   <li>纯黑白：{@link #borderColor()} 沿用基类默认 BLACK；所有单元格背景传 {@code null}（无任何填充），
 *       仅靠边框线条勾勒——这是与 PI（彩色填充）在「同一套规律」下的黑白分支。</li>
 *   <li>版面框架（由代码读取原 PDF 坐标还原）：公司抬头 + 居中大标题 → META 双列盒
 *       (DATE/SHIPMENT · INV.NO/TO) → <b>拆为 4 段独立方法</b>（renderPartiesTable / renderItemTable /
 *       renderTotalsTable / renderPaymentTermsTable），每段仍是同一 5 列网格（{@link #BODY_COL_WIDTHS}），
 *       以 spacing=0 直接叠放 → 列线贯穿、各段零间隙，视觉上仍为<b>单一连续框</b>
 *       （与原单体表完全一致）。代码拆分（可单测/可复用）与视觉连续由此解耦，贴合 PI 已采用的分解结构。</li>
 *   <li>线条整体加粗：覆写 {@link #borderWidth()} 为 0.9（PI 仍为基类默认 0.6，互不影响）。</li>
 * </ul>
 *
 * <p>字体规范与 PI 完全一致：英文 Carlito 四字重 + 中文 Noto Sans SC 子集（glyf/TrueType），
 * 字号沿用基类 {@code FS_*} 六档语义（顶部公司名 FS_TITLE、大标题 FS_H1、分区标题 FS_H2、
 * 字段标签 FS_LABEL、内容与值 FS_BODY、辅助小字 FS_MICRO），确保 PI/CI/PL 三件套视觉规律一致。</p>
 */
@Slf4j
@Component
public class CiPdfRenderer extends AbstractTradePdfRenderer {

    // 列宽尽量取整（相对权重）：ITEMS=2 / DESCRIPTION=8 / UNIT PRICE=4 / QTY=3 / AMOUNT=3（合计 20）。
    // EXPORTER 跨前 2 列(2+8=10)、CONSIGNEE 跨后 3 列(4+3+3=10) → 正好等分，列线对齐、中竖线居中。
    // 整张 body 表共用此 5 列网格，TOTAL/LESS/NET 标签跨前 4 列、金额落第 5 列，列线贯穿一致。
    private static final float[] BODY_COL_WIDTHS = {2f, 8f, 4f, 3f, 3f};
    private static final String CI_DECLARATION = ""; // 商业发票无声明句，仅保留页脚横线 + 公司 | Page

    // NET 强调双线（按原 PDF 实测：金额列两条 1.40pt 线、净间隙 1.40pt，标签区仅单线）
    private static final float NET_RULE_WIDTH = 1.4f;                           // 双线各自的线宽
    private static final float NET_RULE_GAP = 1.4f;                             // 两条线之间的净间隙
    private static final float NET_RULE_HEIGHT = NET_RULE_WIDTH + NET_RULE_GAP; // 两条线中心间距 = 2.8pt

    @Override
    protected Color borderColor() {
        return BLACK;
    }

    /** CI 线条整体加粗（PI 仍用基类默认 0.6，互不影响） */
    @Override
    protected float borderWidth() {
        return 0.9f;
    }

    // =====================================================================
    //  主流程
    // =====================================================================

    public byte[] render(CommercialInvoice ci) {
        ProformaInvoice pi = ci != null ? ci.getProformaInvoice() : null;
        ProformaDetails details = pi != null ? pi.getDetails() : null;
        ProformaDetails.BuyerInfo buyer = details != null ? details.buyer() : null;

        Document doc = new Document(PageSize.A4, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterEvent(sellerCompanyName(details != null ? details.seller() : null),
                    CI_DECLARATION, BLACK, BLACK));
            doc.open();

            // 顶部横线已移除（按需求）
            doc.add(blank(16));
            renderCompanyHeader(doc, details);
            doc.add(blank(2));
            renderTitle(doc);
            doc.add(blank(8));
            renderMetaTable(doc, ci, pi, details, buyer);
            doc.add(blank(16)); // META 与下方连续 body 表之间的分段间隙
            renderPartiesTable(doc, ci, pi, details, buyer);
            renderItemTable(doc, pi);
            InvoiceMoney m = calcMoney(ci, pi);
            renderTotalsTable(doc, m);
            renderPaymentTermsTable(doc, m);
            doc.add(blank(8));
            renderSay(doc, ci, pi, details);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成 CI PDF 失败：" + e.getMessage(), e);
        }
    }

    // =====================================================================
    //  各段落（纯线条 / 无背景）
    // =====================================================================

    /** 1. 公司抬头：英文公司名（左，FS_BODY 加粗，黑），无中文名、无填充、无框 */
    private void renderCompanyHeader(Document doc, ProformaDetails details) {
        String companyName = sellerCompanyName(details != null ? details.seller() : null);
        if (companyName.isEmpty()) {
            companyName = "SELLER";
        }
        add(doc, para(companyName.toUpperCase(), FS_TITLE, Font.BOLD, BLACK, 4));
    }

    /** 2. 大标题：COMMERCIAL INVOICE（居中，FS_H1 加粗，黑，无框无填充） */
    private void renderTitle(Document doc) {
        Paragraph p = boldParagraph("COMMERCIAL INVOICE", FS_H1, 8, BLACK);
        p.setAlignment(Element.ALIGN_CENTER);
        add(doc, p);
    }

    /** 3. META 双列盒：左 = DATE + PORT OF LOADING，右 = INV. NO. + PORT OF DESTINATION（标签与值全为黑，盒子带边框、无填充） */
    private void renderMetaTable(Document doc, CommercialInvoice ci, ProformaInvoice pi,
                                 ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        ProformaDetails.SellerInfo seller = details != null ? details.seller() : null;
        String shipment = seller != null && seller.address() != null ? seller.address() : "";
        String to = buyer != null && buyer.address() != null ? buyer.address() : "";

        Phrase datePh = kvPhrase("DATE:  ",
                ci != null && ci.getInvoiceDate() != null ? formatDate(ci.getInvoiceDate()) : "",
                Font.BOLD, BLACK, BLACK);
        Phrase shipPh = kvPhrase("PORT OF LOADING:  ", safe(shipment), Font.BOLD, BLACK, BLACK);
        Phrase invPh = kvPhrase("INV. NO.:  ", safe(pi != null ? pi.getInvoiceNumber() : ""), Font.BOLD, BLACK, BLACK);
        Phrase toPh = kvPhrase("PORT OF DESTINATION:  ", safe(to), Font.BOLD, BLACK, BLACK);

        // 行1 = DATE(左) + PORT OF LOADING(右)，行2 = INV.NO(左) + PORT OF DESTINATION(右)；
        // 用 2×1 表（每格两行），与原 PDF 无内部分隔线吻合。
        table.addCell(metaCell(datePh, invPh));
        table.addCell(metaCell(shipPh, toPh));
        add(doc, table);
    }

    /** 新建一张 5 列 body 网格表（统一列宽、零间距，保证各段列线贯穿对齐） */
    private PdfPTable newBodyTable() {
        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(BODY_COL_WIDTHS);
        t.setSpacingBefore(0);
        t.setSpacingAfter(0);
        return t;
    }

    /** 4.1 EXPORTER / CONSIGNEE 段（5 列：跨 2 + 跨 3，列线对齐） */
    private void renderPartiesTable(Document doc, CommercialInvoice ci, ProformaInvoice pi,
                                    ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPTable table = newBodyTable();
        PdfPCell exporter = partyCell(true, details, buyer);
        exporter.setColspan(2);
        PdfPCell consignee = partyCell(false, details, buyer);
        consignee.setColspan(3);
        table.addCell(exporter);
        table.addCell(consignee);
        add(doc, table);
    }

    /**
     * 4.2 货物明细段（表头 + 明细行，5 列网格）。
     * 列序对齐原单据：<b>列1 ITEMS = HS Code + Name</b>（编码在上、品名在下），列2 DESCRIPTION OF GOODS = 规格明细，
     * 列3 UNIT PRICE、列4 QTY、列5 AMOUNT。原单据该表列1 不放行号，故不再输出序号。
     */
    private void renderItemTable(Document doc, ProformaInvoice pi) {
        PdfPTable table = newBodyTable();
        table.addCell(headerCell("ITEMS", null, BLACK, FS_LABEL));
        table.addCell(headerCell("DESCRIPTION OF GOODS", null, BLACK, FS_LABEL));
        table.addCell(headerCell("UNIT PRICE", null, BLACK, FS_LABEL));
        table.addCell(headerCell("QTY", null, BLACK, FS_LABEL));
        table.addCell(headerCell("AMOUNT", null, BLACK, FS_LABEL));

        List<QuoteDetailGroup> groups = resolveGroups(pi != null ? pi.getQuotation() : null);
        if (groups == null || groups.isEmpty()) {
            table.addCell(cell("—", FS_BODY, Font.NORMAL, BLACK, null, Element.ALIGN_CENTER, PAD, borderWidth()));
            table.addCell(cell("(无明细)", FS_LABEL, Font.ITALIC, BLACK, null, Element.ALIGN_LEFT, PAD, borderWidth()));
            addEmptyItemCells(table, 3);
        } else {
            for (QuoteDetailGroup g : groups) {
                List<QuoteDetailItem> items = g.items() != null ? g.items() : List.of();
                if (items.isEmpty()) {
                    table.addCell(ciNameCell(g.name(), g.hsCode()));
                    table.addCell(ciDescCell(g.name(), "—"));
                    addEmptyItemCells(table, 3);
                    continue;
                }
                for (QuoteDetailItem it : items) {
                    table.addCell(ciNameCell(g.name(), g.hsCode()));                          // 列1 = HS Code + Name
                    table.addCell(ciDescCell(g.name(), it.description()));                     // 列2 = 加粗品名头 + 规格明细
                    table.addCell(unitPriceCell(it.unitPrice(), it.unit(), it.currency(), BLACK, BLACK, null));  // 列3 = UNIT PRICE（纯线条无填充）
                    table.addCell(qtyCell(it.quantity(), it.unit(), BLACK, BLACK, null));                        // 列4 = QTY
                    table.addCell(totalPriceCell(it.subtotal(), it.currency(), BLACK, BLACK, null));             // 列5 = AMOUNT
                }
            }
        }
        add(doc, table);
    }

    /** 补 n 个空单元格（padding 与货物表其余单元格一致） */
    private void addEmptyItemCells(PdfPTable table, int n) {
        for (int i = 0; i < n; i++) {
            table.addCell(cell("", FS_BODY, Font.NORMAL, BLACK, null, Element.ALIGN_CENTER, PAD, borderWidth()));
        }
    }

    /**
     * 货物表列1（ITEMS）：HS Code + Name，标签独占一行、值在下一行（对齐原单据列序：编码在上、品名在下）。
     * 基类 nameCell 是 Name 在前，此处按原单据调整为编码/品名分列；单元格骨架统一走基类 {@link #borderedCell}（PAD 内边距）。
     */
    private PdfPCell ciNameCell(String name, String hsCode) {
        PdfPCell c = borderedCell(Element.ALIGN_MIDDLE, null);
        String code = safe(hsCode);
        String nm = safe(name);
        if (code.isEmpty() && nm.isEmpty()) {
            c.addElement(blank(9));
            return c;
        }
        if (!code.isEmpty()) {
            c.addElement(para("HS Code:", FS_LABEL, Font.BOLD | Font.ITALIC, BLACK, 0));
            c.addElement(para(code, FS_BODY, Font.NORMAL, BLACK, 4f));
        }
        if (!nm.isEmpty()) {
            c.addElement(para("Name:", FS_LABEL, Font.BOLD | Font.ITALIC, BLACK, 0));
            c.addElement(para(nm, FS_BODY, Font.NORMAL, BLACK, 0));
        }
        return c;
    }

    /**
     * 货物表列2（DESCRIPTION OF GOODS）：顶部先输出加粗品名头（对齐 SAHIL FAB 参考），其下逐行输出规格明细。
     * 配色走 CI 的 BLACK；PI 的对应方法 {@link #piDescCell} 仅配色不同（NAVY/TXT2），结构一致（差异只在配色）。
     */
    private PdfPCell ciDescCell(String name, String desc) {
        PdfPCell c = borderedCell(Element.ALIGN_MIDDLE, null);
        String safeName = safe(name);
        String safeDesc = safe(desc);
        if (safeName.isEmpty() && safeDesc.isEmpty()) {
            c.addElement(blank(9));
            return c;
        }
        // 顶部加粗品名头（与列1 的 Name 呼应，符合 SAHIL FAB 原单据列2 样式）
        if (!safeName.isEmpty()) {
            c.addElement(para(safeName, FS_BODY, Font.BOLD, BLACK, 3f));
        }
        if (safeDesc.isEmpty()) {
            c.addElement(blank(9));
            return c;
        }
        // OpenPDF 对含 \n 的单 Paragraph setLeading 不生效，必须拆行后各自控制 leading + spacingAfter
        String[] lines = safeDesc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            c.addElement(para(line.isBlank() ? " " : line, FS_BODY, Font.NORMAL, BLACK,
                    i < lines.length - 1 ? 2f : 0f));
        }
        return c;
    }

    /** 4.3 TOTAL / LESS / NET 段（标签跨 4 列 + 金额第 5 列；NET 盒底加粗线作视觉标识） */
    private void renderTotalsTable(Document doc, InvoiceMoney m) {
        PdfPTable table = newBodyTable();

        PdfPCell tLabel = cell(m.totalLabel, FS_BODY, Font.BOLD, BLACK, null, Element.ALIGN_LEFT, PAD, borderWidth());
        tLabel.setColspan(4);
        table.addCell(tLabel);
        table.addCell(cell(m.ccy + " " + fmtMoney(m.total), FS_BODY, Font.BOLD, BLACK, null, Element.ALIGN_RIGHT, PAD, borderWidth()));

        PdfPCell lLabel = cell("LESS:  " + m.pctStr + "% ADVANCE PAYMENT RECEIVED VIA " + m.depMethod,
                FS_BODY, Font.NORMAL, BLACK, null, Element.ALIGN_LEFT, PAD, borderWidth());
        lLabel.setColspan(4);
        table.addCell(lLabel);
        table.addCell(cell(m.ccy + " " + fmtMoney(m.advance), FS_BODY, Font.BOLD, BLACK, null, Element.ALIGN_RIGHT, PAD, borderWidth()));

        PdfPCell nLabel = cell("NET INVOICE VALUE", FS_BODY, Font.BOLD, BLACK, null, Element.ALIGN_LEFT, PAD, borderWidth());
        nLabel.setColspan(4);
        table.addCell(nLabel);
        PdfPCell netAmt = cell(m.ccy + " " + fmtMoney(m.net), FS_BODY, Font.BOLD, BLACK, null, Element.ALIGN_RIGHT, PAD, borderWidth());
        // 强调双线由单元格事件自绘（见 NetRuleEvent）：单元格四边保持统一 borderWidth()，
        // 否则 OpenPDF 会改用逐边 line 绘制并把竖线内缩约 0.5pt，导致金额列外框与上下行错位。
        netAmt.setCellEvent(new NetRuleEvent());
        table.addCell(netAmt);
        add(doc, table);
    }

    /**
     * NET 金额单元格的强调双线（对齐原 PDF：金额列双线各 1.40pt、净间隙 1.40pt，标签区保持单线）。
     *
     * <p>不能用「加粗底边框」实现：一旦某条边框宽度与其余边不同，OpenPDF 会放弃整体 rect 绘制而改为
     * 逐边 line 绘制，并把竖线向内收缩约 0.5pt，导致金额单元格外框与上下行错位（实测 480.6→481.0、
     * 559.0→558.5）。因此单元格四边统一为 {@link #borderWidth()}，双线在此事件里按坐标直接绘制。
     *
     * <p>第二条线落在下方 PAYMENT TERMS 单元格的顶部留白内（距第一条 2.8pt），与该区文字不相交。
     */
    private final class NetRuleEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle pos, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            cb.saveState();
            cb.setLineWidth(NET_RULE_WIDTH);
            cb.setColorStroke(borderColor());
            float left = pos.getLeft();
            float right = pos.getRight();
            float y1 = pos.getBottom();                    // 第一条：贴单元格底边
            float y2 = y1 - NET_RULE_HEIGHT;               // 第二条：下移（净间隙 + 线宽）
            cb.moveTo(left, y1);
            cb.lineTo(right, y1);
            cb.moveTo(left, y2);
            cb.lineTo(right, y2);
            cb.stroke();
            cb.restoreState();
        }
    }

    /** 4.4 PAYMENT TERMS 段（跨 5 列，紧跟 NET 零间隙；顶边交由 NET 第二条线承担，故去自身顶边避免覆盖） */
    private void renderPaymentTermsTable(Document doc, InvoiceMoney m) {
        PdfPTable table = newBodyTable();
        PdfPCell pCell = new PdfPCell();
        pCell.setColspan(5);
        pCell.setBorderWidth(borderWidth());
        // 顶边保持与其他边一致：设为 0 会让四边宽度不一致，OpenPDF 改用逐边 line 绘制并把竖线内缩约 0.5pt，
        // 与外框其他行错位。此处 0.9pt 细线会落在 1.4pt 的第二条强调线上，被完全覆盖，不影响双线观感。
        pCell.setBorderColor(borderColor());
        pCell.setPadding(PAD); // 与 TOTAL/LESS/NET 行一致：左边距对齐上方标签、整体收紧
        pCell.setVerticalAlignment(Element.ALIGN_TOP);
        pCell.addElement(boldParagraph("PAYMENT TERMS", FS_H2, 1, BLACK));
        pCell.addElement(para(m.line1, FS_BODY, Font.NORMAL, BLACK, 0));
        pCell.addElement(para(m.line2, FS_BODY, Font.NORMAL, BLACK, 0));
        table.addCell(pCell);
        add(doc, table);
    }

    /** body 金额汇总载体：TOTAL/LESS/NET 与 PAYMENT TERMS 共用，单一数据来源 */
    private static final class InvoiceMoney {
        final String ccy;
        final String totalLabel;
        final String pctStr;
        final String depMethod;
        final String line1;
        final String line2;
        final BigDecimal total;
        final BigDecimal advance;
        final BigDecimal net;

        InvoiceMoney(String ccy, String totalLabel, String pctStr, String depMethod,
                     String line1, String line2, BigDecimal total, BigDecimal advance, BigDecimal net) {
            this.ccy = ccy; this.totalLabel = totalLabel; this.pctStr = pctStr; this.depMethod = depMethod;
            this.line1 = line1; this.line2 = line2; this.total = total; this.advance = advance; this.net = net;
        }
    }

    /** 汇总 TOTAL / LESS(NET) / 定金比例 / 付款条款文案（与 PI 同套金额算法，CI 仅黑线条呈现） */
    private InvoiceMoney calcMoney(CommercialInvoice ci, ProformaInvoice pi) {
        List<QuoteDetailGroup> groups = resolveGroups(pi != null ? pi.getQuotation() : null);
        BigDecimal total = sumTotalsByCcy(groups).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pct = ci != null && ci.getDepositPercentage() != null ? ci.getDepositPercentage() : BigDecimal.ZERO;
        BigDecimal advance = total.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = total.subtract(advance);
        String ccy = primaryCurrency(groups);
        String inc = safe(pi != null && pi.getDetails() != null ? pi.getDetails().incoterms() : null);
        String totalLabel = (inc.isEmpty() ? "FOB QINGDAO, CHINA (Incoterms 2020)" : inc);
        String pctStr = pct.stripTrailingZeros().toPlainString();
        String balPctStr = BigDecimal.valueOf(100).subtract(pct).stripTrailingZeros().toPlainString();
        String depMethod = safe(ci != null ? ci.getDepositPaymentMethod() : "");
        String balMethod = safe(ci != null ? ci.getBalancePaymentMethod() : "");
        String line1 = pctStr + "% " + depMethod + " (Down Payment): " + ccy + " " + fmtMoney(advance) + " — RECEIVED";
        String line2 = balPctStr + "% " + balMethod + ": " + ccy + " " + fmtMoney(net) + " — DUE";
        return new InvoiceMoney(ccy, totalLabel, pctStr, depMethod, line1, line2, total, advance, net);
    }

    /** 5. SAY…ONLY. 行（在外框之外，左对齐，黑，无框） */
    private void renderSay(Document doc, CommercialInvoice ci, ProformaInvoice pi, ProformaDetails details) {
        InvoiceMoney m = calcMoney(ci, pi);
        add(doc, boldParagraph("SAY  " + amountInWordsEn(m.net, m.ccy) + "  ONLY.", FS_BODY, 0, BLACK));
    }

    // =====================================================================
    //  CI 专属小部件
    // =====================================================================

    /** EXPORTER / CONSIGNEE 单元格：标签加粗 + 联系信息（全黑、带边框、无填充、顶对齐）。isExporter=true 取卖方，false 取买方。 */
    private PdfPCell partyCell(boolean isExporter, ProformaDetails details, ProformaDetails.BuyerInfo buyer) {
        PdfPCell c = borderedCell(Element.ALIGN_TOP, null);

        if (isExporter) {
            ProformaDetails.SellerInfo seller = details != null ? details.seller() : null;
            c.addElement(labelParagraph("E X P O R T E R"));
            if (seller != null) {
                if (nonBlank(seller.companyName())) c.addElement(boldParagraph(seller.companyName(), FS_BODY, 2, BLACK));
                if (nonBlank(seller.address())) c.addElement(kvLineParagraph("ADD: ", seller.address(), Font.BOLD, BLACK, BLACK));
                if (nonBlank(seller.phone())) c.addElement(kvLineParagraph("TEL: ", seller.phone(), Font.BOLD, BLACK, BLACK));
                if (nonBlank(seller.email())) c.addElement(kvLineParagraph("EMAIL: ", seller.email(), Font.BOLD, BLACK, BLACK));
            }
        } else {
            c.addElement(labelParagraph("C O N S I G N E E"));
            if (buyer != null) {
                if (nonBlank(buyer.companyName())) c.addElement(boldParagraph(buyer.companyName(), FS_BODY, 2, BLACK));
                if (nonBlank(buyer.registrationNo())) c.addElement(kvLineParagraph("REGISTRATION NO.: ", buyer.registrationNo(), Font.BOLD, BLACK, BLACK));
                if (nonBlank(buyer.address())) c.addElement(kvLineParagraph("ADD: ", buyer.address(), Font.BOLD, BLACK, BLACK));
            }
        }
        return c;
    }

    /** META 单元格：承载多行「标签: 值」短语（无填充，仅带边框） */
    private PdfPCell metaCell(Phrase... lines) {
        PdfPCell c = borderedCell(Element.ALIGN_MIDDLE, null);
        for (Phrase p : lines) {
            Paragraph para = new Paragraph(p);
            para.setLeading(0, LEADING);
            para.setSpacingAfter(3f);
            c.addElement(para);
        }
        return c;
    }
}
