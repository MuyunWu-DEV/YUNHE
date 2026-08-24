package com.yunhe.website.crm.dto.request;

import com.yunhe.website.common.validation.ReviseGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 形式发票创建 / 编辑表单 DTO。
 */
@Data
public class ProformaInvoiceForm {

    private Long id;

    /** 发票号（自动生成，展示用） */
    private String invoiceNumber;

    @NotNull(message = "发票日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate invoiceDate;

    /** 买方（客户）ID */
    private Long customerId;

    /** 来源报价单 ID */
    private Long quotationId;

    /** Seller（卖方，长文本） */
    private String seller;

    /** Buyer - 公司名 */
    private String buyerCompanyName;

    /** Buyer - 注册号 */
    private String buyerRegistrationNo;

    /** Buyer - 地址 */
    private String buyerAddress;

    /** Terms（条款） */
    private String terms;

    /** Incoterms（贸易条款，如 FOB CHINA PORT） */
    private String incoterms;

    /** Bank Account Information（银行账户信息） */
    private String bankAccountInformation;

    /** 变更原因（仅「发起变更」时必填，由 ReviseGroup 分组校验） */
    @NotBlank(groups = ReviseGroup.class, message = "变更原因不能为空")
    private String changeReason;
}
