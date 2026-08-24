package com.yunhe.website.system.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统设置（单例）：维护形式发票默认的 Seller / Terms / Bank Account Information。
 */
@Getter
@Setter
@Entity
@Table(name = "system_settings")
public class SystemSettings extends BaseEntity {

    /** 卖方信息（长文本） */
    @Column(columnDefinition = "text")
    private String seller;

    /** 条款（长文本） */
    @Column(columnDefinition = "text")
    private String terms;

    /** 贸易条款（如 FOB QINGDAO PORT） */
    @Column(columnDefinition = "text")
    private String incoterms;

    /** 银行账户信息（长文本） */
    @Column(name = "bank_account_information", columnDefinition = "text")
    private String bankAccountInformation;
}
