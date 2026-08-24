package com.yunhe.website.crm;

import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.CustomerFile;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.QuotationStatus;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.repository.CustomerFileRepository;
import com.yunhe.website.crm.repository.CustomerRepository;
import com.yunhe.website.crm.repository.QuotationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM 实体集成测试：验证 details JSON 与附件 BLOB 的存取。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrmEntityTests {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private CustomerFileRepository customerFileRepository;

    @Test
    void quotationDetailsJsonRoundTrip() {
        Customer customer = new Customer();
        customer.setName("测试客户");
        customer.setPhone("13900000001");
        customerRepository.save(customer);

        Quotation quotation = new Quotation();
        quotation.setQuoteDate(LocalDate.now());
        quotation.setStatus(QuotationStatus.DRAFT);
        quotation.setCustomer(customer);

        List<QuoteDetailGroup> details = List.of(
                new QuoteDetailGroup("服务器", "84713090", List.of(
                        new QuoteDetailItem("配置A", new BigDecimal("100.00"), 2, "SETS", "USD"),
                        new QuoteDetailItem("配置B", new BigDecimal("200.50"), 1, "SETS", "USD"))),
                new QuoteDetailGroup("硬盘", "84717090", List.of(
                        new QuoteDetailItem("配置C", new BigDecimal("50.00"), 4, "PCS", "USD"))));
        quotation.setDetails(details);
        quotationRepository.save(quotation);

        Quotation loaded = quotationRepository.findById(quotation.getId()).orElseThrow();
        assertThat(loaded.getDetails()).hasSize(2);
        assertThat(loaded.getDetails().get(0).name()).isEqualTo("服务器");
        assertThat(loaded.getDetails().get(0).hsCode()).isEqualTo("84713090");
        assertThat(loaded.getDetails().get(0).items()).hasSize(2);
        assertThat(loaded.getDetails().get(0).items().get(0).unitPrice()).isEqualByComparingTo("100.00");
        assertThat(loaded.getDetails().get(0).items().get(0).quantity()).isEqualTo(2);
        assertThat(loaded.getDetails().get(0).items().get(0).unit()).isEqualTo("SETS");
        assertThat(loaded.getDetails().get(0).items().get(0).currency()).isEqualTo("USD");
    }

    @Test
    void customerFileBlobRoundTrip() {
        Customer customer = new Customer();
        customer.setName("附件客户");
        customer.setPhone("13900000002");
        customerRepository.save(customer);

        CustomerFile file = new CustomerFile();
        file.setCustomer(customer);
        file.setOriginalName("test.txt");
        file.setContentType("text/plain");
        file.setSize(5);
        file.setContent(new byte[]{1, 2, 3, 4, 5});
        customerFileRepository.save(file);

        CustomerFile loaded = customerFileRepository.findById(file.getId()).orElseThrow();
        assertThat(loaded.getContent()).containsExactly(1, 2, 3, 4, 5);
        assertThat(loaded.getOriginalName()).isEqualTo("test.txt");
    }
}
