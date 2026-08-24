package com.yunhe.website.crm.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户档案。
 */
@Getter
@Setter
@Entity
@Table(name = "crm_customer")
public class Customer extends BaseEntity {

    /** 客户姓名（唯一索引） */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** 手机号 */
    @Column(nullable = false, length = 20)
    private String phone;

    /** 公司 */
    @Column(length = 100)
    private String company;

    /** 注册号 */
    @Column(name = "registration_no", length = 50)
    private String registrationNo;

    /** 地址 */
    @Column(length = 255)
    private String address;

    /** 客户标签 */
    @ElementCollection
    @CollectionTable(name = "crm_customer_tag", joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "tag", length = 50)
    @Enumerated(EnumType.STRING)
    private Set<CustomerTag> tags = new LinkedHashSet<>();

    /** 优先级 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CustomerPriority priority = CustomerPriority.MEDIUM;

    /** 下次跟进时间（到天） */
    @Column(name = "next_follow_up_at")
    private LocalDate nextFollowUpAt;

    /** 附件留档 */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerFile> files = new ArrayList<>();
}
