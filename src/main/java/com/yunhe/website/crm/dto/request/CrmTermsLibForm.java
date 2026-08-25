package com.yunhe.website.crm.dto.request;

import lombok.Data;

/**
 * 条款库表单 DTO（结构化 19 字段）。
 */
@Data
public class CrmTermsLibForm {

    private Long id;

    // ===== 公司 =====
    private String companyNameChinese;
    private String companyNameEnglish;
    private String address;
    private String phone;
    private String email;

    // ===== 银行账户 =====
    private String beneficiaryName;
    private String beneficiaryAddress;
    private String bankName;
    private String bankAddress;
    private String accountNo;
    private String swiftCode;

    // ===== 条款 =====
    private String countryOfOrigin;
    private String portOfDelivery;
    private String timeOfDelivery;
    private String paymentTerm;
    private String packing;
    private String note;

    // ===== 其他 =====
    private String incoterms;
    private String warranty;
}
