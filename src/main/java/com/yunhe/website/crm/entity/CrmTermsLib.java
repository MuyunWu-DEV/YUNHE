package com.yunhe.website.crm.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 条款库（单例）：PI 单证默认的卖方 / 银行账户 / 条款 / 其他信息，用结构化列存储。
 *
 * <p>单例表（一行），供形式发票「一键导入」默认数据。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "crm_terms_lib")
public class CrmTermsLib extends BaseEntity {

    // ===== 公司 =====
    /** 公司中文名 */
    @Column(name = "company_name_chinese", columnDefinition = "text")
    private String companyNameChinese;

    /** 公司英文名 */
    @Column(name = "company_name_english", columnDefinition = "text")
    private String companyNameEnglish;

    /** 地址 */
    @Column(columnDefinition = "text")
    private String address;

    /** 电话 */
    @Column(length = 50)
    private String phone;

    /** 邮箱 */
    @Column(length = 100)
    private String email;

    // ===== 银行账户 =====
    /** 收款人名称 */
    @Column(name = "beneficiary_name", columnDefinition = "text")
    private String beneficiaryName;

    /** 收款人地址 */
    @Column(name = "beneficiary_address", columnDefinition = "text")
    private String beneficiaryAddress;

    /** 银行名称 */
    @Column(name = "bank_name", columnDefinition = "text")
    private String bankName;

    /** 银行地址 */
    @Column(name = "bank_address", columnDefinition = "text")
    private String bankAddress;

    /** 账号 */
    @Column(name = "account_no", length = 100)
    private String accountNo;

    /** SWIFT 代码 */
    @Column(name = "swift_code", length = 100)
    private String swiftCode;

    // ===== 条款 =====
    /** 原产国 */
    @Column(name = "country_of_origin", columnDefinition = "text")
    private String countryOfOrigin;

    /** 交货港 */
    @Column(name = "port_of_delivery", columnDefinition = "text")
    private String portOfDelivery;

    /** 交货时间 */
    @Column(name = "time_of_delivery", columnDefinition = "text")
    private String timeOfDelivery;

    /** 付款条款 */
    @Column(name = "payment_term", columnDefinition = "text")
    private String paymentTerm;

    /** 包装 */
    @Column(columnDefinition = "text")
    private String packing;

    /** 备注 */
    @Column(columnDefinition = "text")
    private String note;

    // ===== 其他 =====
    /** 贸易条款（如 FOB CHINA PORT） */
    @Column(columnDefinition = "text")
    private String incoterms;

    /** 质保条款 */
    @Column(columnDefinition = "text")
    private String warranty;
}
