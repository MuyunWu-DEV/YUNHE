package com.yunhe.website.crm;

import com.yunhe.website.crm.entity.PackingLine;
import com.yunhe.website.crm.entity.PackingList;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.support.PlPdfRenderer;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 一次性手动验证：按参考单据数据构造 mock 装箱单（含 RouteInfo、报价单项带稳定 key、lines 装箱数据），
 * 经 quoteLineKey JOIN 渲染为 PDF 写到 workspace，肉眼对比真实装箱单。
 */
class PlPdfRendererTest {

    @Test
    void renderMockPl() throws Exception {
        ProformaDetails details = new ProformaDetails(
                new ProformaDetails.SellerInfo(
                        "Qingdao Yunhe Intelligent Manufacturing Co., Ltd.",
                        "Wangtai Industrial Park, Qingdao, Shandong, China 266425",
                        "+86 19853207766",
                        "karoljiang@126.com",
                        "青岛云合智能制造有限公司"),
                new ProformaDetails.BuyerInfo(
                        "VIDHI FASHION",
                        "24AKCPD9513H1ZH",
                        "GROUND FLOOR, 82, KRISHNA INDUSTRIAL ESTATE-2, KAMREJ ROAD, SURAT, Surat, Gujarat, 394185"),
                "FOB Qingdao, China",
                "Country of Origin: The People's Republic of China\n"
                        + "Port of Delivery: From Qingdao, China to Nhava Sheva, India\n"
                        + "Packing: Export seaworthy packing",
                "Bank Name: JPMorgan Chase Bank N.A., Hong Kong Branch\n"
                        + "Account No.: 200000000347964",
                "One year after-sales service warranty on electrical accessories under correct operation.",
                new ProformaDetails.RouteInfo("Qingdao, China", "Nhava Sheva, India"));

        // 报价单：两个明细项，各自带稳定 key（PL 经此 key JOIN）
        Quotation quotation = new Quotation();
        quotation.setQuoteDate(LocalDate.of(2026, 7, 10));
        quotation.setDetails(List.of(
                new QuoteDetailGroup(
                        "Brand New Shuttleless Water Jet Looms",
                        "84463090",
                        List.of(
                                new QuoteDetailItem(
                                        "qi-1",
                                        "- Model Number: YRW-8101,\nWorking Width: 190 cm,\n"
                                                + "Xinliao Electronic Panel with Double Feeders,\n"
                                                + "8 Pcs Yurun Heald Frame, 2 Cloth Roll, 2 Beam, 2 Reed,\n"
                                                + "20,000 Heald Wire\n- WIR 1100 MPM",
                                        new BigDecimal("11500"),
                                        24,
                                        "SETS",
                                        "USD"),
                                new QuoteDetailItem(
                                        "qi-2",
                                        "- Model Number: YRW-8102,\nWorking Width: 230 cm,\n"
                                                + "Single Nozzle, Motor,\nNiupai 410 Cam with 8 Shafts",
                                        new BigDecimal("800"),
                                        4,
                                        "SETS",
                                        "USD")))));

        // 装箱单：lines 仅装箱数据，经 quoteLineKey 关联报价单项
        PackingList pl = new PackingList();
        pl.setPackingNo("YHPL-2026-001");
        pl.setPackingDate(LocalDate.of(2026, 8, 20));
        pl.setMarks("N/M");
        pl.setLines(List.of(
                new PackingLine("qi-1", 2, new BigDecimal("1200.000"), new BigDecimal("1350.000"), new BigDecimal("2.400")),
                new PackingLine("qi-2", 1, new BigDecimal("300.000"), new BigDecimal("340.000"), new BigDecimal("0.600"))));

        ProformaInvoice pi = new ProformaInvoice();
        pi.setInvoiceNumber("YRPI20260802");
        pi.setInvoiceDate(LocalDate.of(2026, 7, 10));
        pi.setQuotation(quotation);
        pi.setDetails(details);
        pl.setProformaInvoice(pi);

        byte[] pdf = new PlPdfRenderer().render(pl, quotation);
        File out = new File("C:/Users/Muyun/WorkBuddy/YUNHE/pl_mock.pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(pdf);
        }
        System.out.println("PL PDF 写入：" + out + " (" + pdf.length + " bytes)");
    }
}
