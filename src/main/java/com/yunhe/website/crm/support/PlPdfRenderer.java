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
import com.yunhe.website.crm.entity.PackingLine;
import com.yunhe.website.crm.entity.PackingList;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 装箱单（Packing List）PDF 渲染器。
 *
 * <p>继承 {@link AbstractTradePdfRenderer} 复用字体基础设施与底层 cell/phrase 工厂；本类只负责版面与配色。
 * 配色与 CI 同族：纯黑白、{@link #borderColor()} 沿用基类默认 BLACK、线条加粗 {@link #borderWidth()}=0.9、无背景填充。</p>
 *
 * <p><b>数据模型（JOIN，不冗余）</b>：装箱单 {@link PackingList#getLines()} 只存「装箱专属」数据
 * （packages / netWeight / grossWeight / measurement），品名 / HS Code / 描述 / 数量等货物字段不冗余存储，
 * 渲染时按 {@link PackingLine#quoteLineKey()} JOIN 回报价单项（{@link QuoteDetailItem#key()}）取得。
 * 这样货物字段单一来源 = 报价单，保证数据唯一性与一致性；报价单重排也不破坏关联（靠稳定 key，而非数组下标）。</p>
 *
 * <p><b>列宽</b>（7 列，相对权重合计 25）：ITEMS=2 / DESCRIPTION=8 / QTY(SETS)=3 / N.W.(KGS)=3 /
 * G.W.(KGS)=3 / QTY(PKGS)=3 / MEAS(m³)=3。ITEMS 列按品名分组 rowspan 合并（与 CI/明细页一致）。</p>
 */
@Slf4j
@Component
public class PlPdfRenderer extends AbstractTradePdfRenderer {

    // 7 列网格：ITEMS / DESCRIPTION / QTY / N.W. / G.W. / QTY(PKGS) / MEAS
    private static final float[] BODY_COL_WIDTHS = {2f, 8f, 3f, 3f, 3f, 3f, 3f};
    private static final String PL_DECLARATION = ""; // 装箱单无声明句，仅页脚横线 + 公司 | Page

    @Override
    protected Color borderColor() {
        return BLACK;
    }

    /** PL 线条整体加粗（PI 仍用基类默认 0.6，CI 亦 0.9，互不影响） */
    @Override
    protected float borderWidth() {
        return 0.9f;
    }

    // =====================================================================
    //  主流程
    // =====================================================================

    /**
     * 渲染装箱单 PDF。
     *
     * @param pl         装箱单（含 lines 装箱数据 + 来源 PI）
     * @param quotation  根报价单（JOIN 来源，提供品名 / HS Code / 描述 / 数量）
     */
    public byte[] render(PackingList pl, Quotation quotation) {
        ProformaInvoice pi = pl != null ? pl.getProformaInvoice() : null;
        ProformaDetails details = pi != null ? pi.getDetails() : null;

        Document doc = new Document(PageSize.A4, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterEvent(sellerCompanyName(details != null ? details.seller() : null),
                    PL_DECLARATION, BLACK, BLACK));
            doc.open();

            doc.add(blank(16));
            renderCompanyHeader(doc, details);
            doc.add(blank(2));
            renderTitle(doc);
            doc.add(blank(8));
            renderMetaTable(doc, pl, pi, details);
            doc.add(blank(16));
            renderItemTable(doc, pl, quotation);
            renderTotalsTable(doc, pl, quotation);
            renderMarksAndRemark(doc, pl);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成 PL PDF 失败：" + e.getMessage(), e);
        }
    }

    // =====================================================================
    //  各段落（纯线条 / 无背景）
    // =====================================================================

    /** 1. 公司抬头 */
    private void renderCompanyHeader(Document doc, ProformaDetails details) {
        String companyName = sellerCompanyName(details != null ? details.seller() : null);
        if (companyName.isEmpty()) {
            companyName = "SELLER";
        }
        add(doc, para(companyName.toUpperCase(), FS_TITLE, Font.BOLD, BLACK, 4));
    }

    /** 2. 大标题 */
    private void renderTitle(Document doc) {
        Paragraph p = boldParagraph("PACKING LIST", FS_H1, 8, BLACK);
        p.setAlignment(Element.ALIGN_CENTER);
        add(doc, p);
    }

    /** 3. META 双列盒：左 = DATE + INVOICE NO.；右 = PORT OF LOADING + PORT OF DESTINATION（走 route，缺省回退地址） */
    private void renderMetaTable(Document doc, PackingList pl, ProformaInvoice pi, ProformaDetails details) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});

        String date = pl != null && pl.getPackingDate() != null ? formatDate(pl.getPackingDate()) : "";
        String invNo = pi != null && pi.getInvoiceNumber() != null ? pi.getInvoiceNumber() : "";

        Phrase datePh = kvPhrase("DATE:  ", date, Font.BOLD, BLACK, BLACK);
        Phrase invPh = kvPhrase("INVOICE NO.:  ", invNo, Font.BOLD, BLACK, BLACK);
        Phrase polPh = kvPhrase("PORT OF LOADING:  ",
                details != null ? safe(details.portOfLoading()) : "", Font.BOLD, BLACK, BLACK);
        Phrase podPh = kvPhrase("PORT OF DESTINATION:  ",
                details != null ? safe(details.portOfDestination()) : "", Font.BOLD, BLACK, BLACK);

        table.addCell(metaCell(datePh, invPh));
        table.addCell(metaCell(polPh, podPh));
        add(doc, table);
    }

    /** 新建 7 列 body 网格表（统一列宽、零间距，保证列线贯穿） */
    private PdfPTable newBodyTable() {
        PdfPTable t = new PdfPTable(7);
        t.setWidthPercentage(100);
        t.setWidths(BODY_COL_WIDTHS);
        t.setSpacingBefore(0);
        t.setSpacingAfter(0);
        return t;
    }

    /**
     * 4. 货物明细段（表头 + 明细行，7 列网格）。
     * 列1 ITEMS = HS Code + Name（按品名分组 rowspan 合并）；列2 DESCRIPTION = 描述；
     * 列3 QTY = 数量 + 单位（来自 JOIN 的报价单项）；列4 N.W. / 列5 G.W. / 列6 QTY(PKGS) / 列7 MEAS = 装箱数据（来自 lines）。
     */
    private void renderItemTable(Document doc, PackingList pl, Quotation quotation) {
        PdfPTable table = newBodyTable();
        table.addCell(headerCell("ITEMS", null, BLACK, FS_LABEL));
        table.addCell(headerCell("DESCRIPTION OF GOODS", null, BLACK, FS_LABEL));
        table.addCell(headerCell("QTY", null, BLACK, FS_LABEL));
        table.addCell(headerCell("N.W.(KGS)", null, BLACK, FS_LABEL));
        table.addCell(headerCell("G.W.(KGS)", null, BLACK, FS_LABEL));
        table.addCell(headerCell("QTY(PKGS)", null, BLACK, FS_LABEL));
        table.addCell(headerCell("MEAS(m\u00B3)", null, BLACK, FS_LABEL));

        List<JoinedRow> rows = buildJoinedRows(pl, quotation);
        if (rows.isEmpty()) {
            table.addCell(cell("—", FS_BODY, Font.NORMAL, BLACK, null, Element.ALIGN_CENTER, PAD, borderWidth()));
            addEmptyItemCells(table, 6);
        } else {
            for (JoinedRow r : rows) {
                if (r.firstInGroup) {
                    table.addCell(plNameCell(r.groupName, r.hsCode, r.groupItemCount));
                }
                table.addCell(plDescCell(r.groupName, r.item != null ? r.item.description() : ""));
                // 数量来自 JOIN 的报价单项
                int qty = r.item != null ? r.item.quantity() : 0;
                String unit = r.item != null ? safe(r.item.unit()) : "";
                table.addCell(qtyCell(qty, unit, BLACK, BLACK, null));
                // 装箱数据来自 line
                table.addCell(weightCell(r.line != null ? r.line.netWeight() : null, "KGS"));
                table.addCell(weightCell(r.line != null ? r.line.grossWeight() : null, "KGS"));
                table.addCell(pkgCell(r.line != null ? r.line.packages() : null));
                table.addCell(weightCell(r.line != null ? r.line.measurement() : null, "m\u00B3"));
            }
        }
        add(doc, table);
    }

    /** 5. TOTAL 合计行：前 2 列合并为 TOTAL 标签，后 5 列分别填合计（数量来自 JOIN 求和、其余来自 lines 派生） */
    private void renderTotalsTable(Document doc, PackingList pl, Quotation quotation) {
        PdfPTable table = newBodyTable();
        List<JoinedRow> rows = buildJoinedRows(pl, quotation);

        int totalQty = rows.stream()
                .filter(r -> r.item != null)
                .mapToInt(r -> r.item.quantity())
                .sum();
        String unit = rows.stream().map(r -> r.item).filter(java.util.Objects::nonNull)
                .map(QuoteDetailItem::unit).filter(u -> u != null && !u.isBlank())
                .findFirst().orElse("");

        PdfPCell label = cell("TOTAL", FS_BODY, Font.BOLD, BLACK, null, Element.ALIGN_LEFT, PAD, borderWidth());
        label.setColspan(2);
        table.addCell(label);
        table.addCell(qtyCell(totalQty, unit, BLACK, BLACK, null));
        table.addCell(weightCell(pl.getNetWeight(), "KGS"));
        table.addCell(weightCell(pl.getGrossWeight(), "KGS"));
        table.addCell(pkgCell(pl.getNumberOfPackages()));
        table.addCell(weightCell(pl.getVolume(), "m\u00B3"));
        add(doc, table);
    }

    /** 6. 唛头 + 备注（外框之外，纯文本） */
    private void renderMarksAndRemark(Document doc, PackingList pl) {
        if (pl != null && nonBlank(pl.getMarks())) {
            doc.add(blank(8));
            add(doc, boldParagraph("MARKS: " + safe(pl.getMarks()), FS_BODY, 2, BLACK));
        }
        if (pl != null && nonBlank(pl.getRemark())) {
            doc.add(blank(4));
            add(doc, boldParagraph("REMARKS: " + safe(pl.getRemark()), FS_BODY, 0, BLACK));
        }
    }

    // =====================================================================
    //  JOIN 装配（render-time 按 quoteLineKey 关联报价单项）
    // =====================================================================

    /** 渲染层 JOIN 结果：一行 = 一个报价单项 + 其对应装箱行（按 key，缺 key 时按位置回退） */
    private record JoinedRow(
            String groupName,
            String hsCode,
            QuoteDetailItem item,
            PackingLine line,
            boolean firstInGroup,
            int groupItemCount
    ) {
    }

    private List<JoinedRow> buildJoinedRows(PackingList pl, Quotation quotation) {
        List<JoinedRow> result = new ArrayList<>();
        if (pl == null || pl.getLines() == null) {
            return result;
        }
        List<QuoteDetailGroup> groups = resolveGroups(quotation);

        // 1) 建 key → line 索引（仅对已带 key 的行）
        Map<String, PackingLine> byKey = new LinkedHashMap<>();
        for (PackingLine l : pl.getLines()) {
            if (l.quoteLineKey() != null && !l.quoteLineKey().isBlank()) {
                byKey.putIfAbsent(l.quoteLineKey(), l);
            }
        }
        // 2) 全局位置回退：行序与报价单项序一致的兜底
        List<QuoteDetailItem> flat = new ArrayList<>();
        for (QuoteDetailGroup g : groups) {
            if (g.items() != null) flat.addAll(g.items());
        }

        int pos = 0;
        for (QuoteDetailGroup g : groups) {
            List<QuoteDetailItem> items = g.items() != null ? g.items() : List.of();
            int count = items.size();
            for (int i = 0; i < count; i++) {
                QuoteDetailItem item = items.get(i);
                PackingLine line = null;
                if (item.key() != null) {
                    line = byKey.get(item.key());
                }
                if (line == null && pos < pl.getLines().size()) {
                    line = pl.getLines().get(pos); // 位置回退
                }
                result.add(new JoinedRow(g.name(), g.hsCode(), item, line,
                        i == 0, count));
                pos++;
            }
        }
        return result;
    }

    // =====================================================================
    //  PL 专属小部件
    // =====================================================================

    /** ITEMS 列（HS Code + Name），按品名分组 rowspan 合并跨同组各项 */
    private PdfPCell plNameCell(String name, String hsCode, int rowspan) {
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
        if (rowspan > 1) c.setRowspan(rowspan);
        return c;
    }

    /** DESCRIPTION 列：加粗品名头 + 规格明细（与 CI 列2 同构，差异只在配色） */
    private PdfPCell plDescCell(String name, String desc) {
        PdfPCell c = borderedCell(Element.ALIGN_MIDDLE, null);
        String safeName = safe(name);
        String safeDesc = safe(desc);
        if (safeName.isEmpty() && safeDesc.isEmpty()) {
            c.addElement(blank(9));
            return c;
        }
        if (!safeName.isEmpty()) {
            c.addElement(para(safeName, FS_BODY, Font.BOLD, BLACK, 3f));
        }
        if (safeDesc.isEmpty()) {
            c.addElement(blank(9));
            return c;
        }
        String[] lines = safeDesc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            c.addElement(para(line.isBlank() ? " " : line, FS_BODY, Font.NORMAL, BLACK,
                    i < lines.length - 1 ? 2f : 0f));
        }
        return c;
    }

    /** 重量/体积单元格：数值（加粗）+ 单位（micro），居中，无填充 */
    private PdfPCell weightCell(BigDecimal value, String unit) {
        if (value == null) {
            return cell("-", FS_BODY, Font.NORMAL, BLACK, null, Element.ALIGN_CENTER, PAD, borderWidth());
        }
        Phrase ph = new Phrase();
        ph.add(new Chunk(fmtWeight(value) + "\n", textFont(fmtWeight(value), FS_BODY, Font.BOLD, BLACK)));
        ph.add(new Chunk(safe(unit), textFont(safe(unit), FS_MICRO, Font.NORMAL, BLACK)));
        return phraseCell(ph, Element.ALIGN_CENTER, PAD, borderWidth(), null);
    }

    /** 件数单元格：数值（加粗），居中，无填充 */
    private PdfPCell pkgCell(Integer packages) {
        if (packages == null) {
            return cell("-", FS_BODY, Font.NORMAL, BLACK, null, Element.ALIGN_CENTER, PAD, borderWidth());
        }
        return cell(String.valueOf(packages), FS_BODY, Font.BOLD, BLACK, null, Element.ALIGN_CENTER, PAD, borderWidth());
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

    /** 补 n 个空单元格 */
    private void addEmptyItemCells(PdfPTable table, int n) {
        for (int i = 0; i < n; i++) {
            table.addCell(cell("", FS_BODY, Font.NORMAL, BLACK, null, Element.ALIGN_CENTER, PAD, borderWidth()));
        }
    }

    /** 重量格式化：最多 3 位小数，去掉末尾 0 */
    private static String fmtWeight(BigDecimal v) {
        if (v == null) return "-";
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() <= 0) {
            return String.format("%,d", s.toBigInteger());
        }
        return String.format("%,.3f", v);
    }
}
