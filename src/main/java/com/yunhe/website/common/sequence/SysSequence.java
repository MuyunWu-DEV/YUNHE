package com.yunhe.website.common.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 系统序号表（通用原子自增计数器的支撑实体）。
 *
 * <p>以任意字符串为 key（如 {@code YHPI-2026}、{@code CUSTOMER}、{@code TICKET}），
 * 维护「当前已分配的最大序号」，通过 MySQL 原子
 * {@code INSERT ... ON DUPLICATE KEY UPDATE ... LAST_INSERT_ID(...)} 递增，
 * 为各类业务编号（单据号、客户号、工单号等）提供并发安全的取号能力
 * （取号逻辑见 {@link SequenceStore}）。</p>
 *
 * <p>取号实际走原生 SQL，本实体仅用于让开发环境 {@code ddl-auto:update} 自动建表；
 * 生产环境使用 {@code ddl-auto:validate}，需先执行 {@code sql/sys_sequence.sql} 建表。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sys_sequence")
public class SysSequence {

    /** 序号键：业务自定义的任意字符串，如 YHPI-2026、CUSTOMER */
    @Id
    @Column(name = "seq_key", nullable = false, length = 64)
    private String seqKey;

    /** 当前已分配的最大序号 */
    @Column(name = "current_value", nullable = false)
    private int currentValue;

    public SysSequence(String seqKey) {
        this.seqKey = seqKey;
    }
}
