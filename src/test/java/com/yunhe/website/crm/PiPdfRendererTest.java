package com.yunhe.website.crm;

import com.yunhe.website.crm.entity.*;
import com.yunhe.website.crm.support.PiPdfRenderer;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 一次性手动验证：按参考模板数据构造 mock PI，渲染为 PDF 写到 workspace，肉眼对比模板。
 * 验证完可保留作为 PDF 渲染的 smoke test。
 */
class PiPdfRendererTest {

    @Test
    void renderMockPiFromTemplate() throws Exception {
        ProformaDetails details = new ProformaDetails(
                new ProformaDetails.SellerInfo(
                        "Qingdao Yurun Machinery Technology Co., Ltd.",
                        "Wangtai Industrial Park, Qingdao, Shandong, China 266425",
                        "+86 19853207766",
                        "karoljiang@126.com",
                        "青岛钰润机械科技有限公司"),
                new ProformaDetails.BuyerInfo(
                        "VIDHI FASHION",
                        "24AKCPD9513H1ZH",
                        "GROUND FLOOR, 82, KRISHNA INDUSTRIAL ESTATE-2, KAMREJ ROAD, SURAT, Surat, Gujarat, 394185"),
                "FOB Qingdao, China",
                "Country of Origin: The People's Republic of China\n"
                        + "Port of Delivery: From Qingdao, China to Nhava Sheva, India\n"
                        + "Time of Delivery: Within 45 days after receiving the advance payment\n"
                        + "Payment Term: 25% advance by T/T + 75% before loading (Price valid for 30 days)\n"
                        + "Packing: Export seaworthy packing\n"
                        + "Note: Loom and accessory specifications may be adjusted according to the fabric style to be produced.",
                "Bank Name: JPMorgan Chase Bank N.A., Hong Kong Branch\n"
                        + "Bank Address: 16/F Tower 2 The Quayside, 77 Hoi Bun Road, Kwun Tong, Hong Kong\n"
                        + "SWIFT Code: CHASHKHH (CHASHKHHXXX, if 11 characters required)\n"
                        + "Account No.: 200000000347964\n"
                        + "Beneficiary Name: Qingdao Yurun Machinery Technology Co., Ltd.\n"
                        + "Beneficiary Address: Qingdao Yurun Machinery Technology Co., Ltd., Wangtai Town, Huangdao District, Qingdao City, Shandong Province",
                "One year after-sales service warranty on electrical accessories under correct operation.");

        Quotation quotation = new Quotation();
        quotation.setQuoteDate(LocalDate.now());
        quotation.setDetails(List.of(
                new QuoteDetailGroup(
                        "Brand New Shuttleless Water Jet Looms",
                        "84463090",
                        List.of(new QuoteDetailItem(
                                "- Model Number: YRW-8101,\n"
                                        + "Working Width: 190 cm,\n"
                                        + "Xinliao Electronic Panel with Double Feeders, Double Nozzles,\n"
                                        + "X-Motor, MLO / MTU,\n"
                                        + "Niupai 410 Cam with 8 Shafts,\n"
                                        + "8 Pcs Yurun Heald Frame,\n"
                                        + "2 Cloth Roll, 2 Beam, 2 Reed,\n"
                                        + "20,000 Heald Wire\n"
                                        + "- With Standard Accessories\n"
                                        + "- WIR 1100 MPM\n"
                                        + "- HS Code: 84463090",
                                new BigDecimal("11000"),
                                28,
                                "SETS",
                                "USD")))));

        ProformaInvoice invoice = new ProformaInvoice();
        invoice.setInvoiceDate(LocalDate.of(2026, 7, 10));
        invoice.setInvoiceNumber("YRPI20260708");
        invoice.setQuotation(quotation);
        invoice.setDetails(details);

        byte[] pdf = new PiPdfRenderer().render(invoice);
        File out = new File("C:/Users/Muyun/WorkBuddy/YUNHE/pi_mock.pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(pdf);
        }
        System.out.println("PI PDF 写入：" + out + " (" + pdf.length + " bytes)");
    }
}
