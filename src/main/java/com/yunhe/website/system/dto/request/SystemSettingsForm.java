package com.yunhe.website.system.dto.request;

import lombok.Data;

/**
 * 系统设置表单 DTO。
 */
@Data
public class SystemSettingsForm {

    private Long id;

    /** Seller（卖方，长文本） */
    private String seller;

    /** Terms（条款） */
    private String terms;

    /** Incoterms（贸易条款） */
    private String incoterms;

    /** Bank Account Information（银行账户信息） */
    private String bankAccountInformation;
}
