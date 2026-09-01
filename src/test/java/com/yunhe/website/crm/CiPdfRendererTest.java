package com.yunhe.website.crm;

import com.yunhe.website.crm.entity.CommercialInvoice;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.support.CiPdfRenderer;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 一次性手动验证：按参考单据数据构造 mock CI，渲染为 PDF 写到 workspace，肉眼对比原 Commercial Invoice。
 */
class CiPdfRendererTest {

    @Test
    void renderMockCiFromTemplate() throws Exception {
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
                        + "Time of Delivery: Within 45 days after receiving the advance payment\n"
                        + "Payment Term: 25% advance by T/T + 75% before loading (Price valid for 30 days)\n"
                        + "Packing: Export seaworthy packing\n"
                        + "Note: Loom and accessory specifications may be adjusted according to the fabric style to be produced.",
                "Bank Name: JPMorgan Chase Bank N.A., Hong Kong Branch\n"
                        + "Bank Address: 16/F Tower 2 The Quayside, 77 Hoi Bun Road, Kwun Tong, Hong Kong\n"
                        + "SWIFT Code: CHASHKHH (CHASHKHHXXX, if 11 characters required)\n"
                        + "Account No.: 200000000347964\n"
                        + "Beneficiary Name: Qingdao Yunhe Intelligent Manufacturing Co., Ltd.\n"
                        + "Beneficiary Address: Qingdao Yunhe Intelligent Manufacturing Co., Ltd., Wangtai Town, Huangdao District, Qingdao City, Shandong Province",
                "One year after-sales service warranty on electrical accessories under correct operation.",
                new com.yunhe.website.crm.entity.ProformaDetails.RouteInfo(
                        "Qingdao, China", "Nhava Sheva, India"));

        Quotation quotation = new Quotation();
        quotation.setQuoteDate(LocalDate.now());
        quotation.setDetails(List.of(
                new QuoteDetailGroup(
                        "Brand New Shuttleless Water Jet Looms",
                        "84463090",
                        List.of(new QuoteDetailItem(
                                "qi-1",
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
                                new BigDecimal("11500"),
                                24,
                                "SETS",
                                "USD")))));

        ProformaInvoice pi = new ProformaInvoice();
        pi.setInvoiceDate(LocalDate.of(2026, 7, 10));
        pi.setInvoiceNumber("YRPI20260802");
        pi.setQuotation(quotation);
        pi.setDetails(details);

        CommercialInvoice ci = new CommercialInvoice();
        ci.setInvoiceNo("YHINV-2026-001");
        ci.setInvoiceDate(LocalDate.of(2026, 8, 4));
        ci.setDepositPercentage(new BigDecimal("35"));
        ci.setDepositPaymentMethod("T/T in advance");
        ci.setBalancePaymentMethod("T/T after B/L copy");
        ci.setProformaInvoice(pi);

        byte[] pdf = new CiPdfRenderer().render(ci);
        File out = new File("C:/Users/Muyun/WorkBuddy/YUNHE/ci_mock.pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(pdf);
        }
        System.out.println("CI PDF 写入：" + out + " (" + pdf.length + " bytes)");
    }
}
