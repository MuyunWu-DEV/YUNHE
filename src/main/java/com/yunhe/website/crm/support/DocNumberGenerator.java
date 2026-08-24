package com.yunhe.website.crm.support;

/**
 * 单据号生成器：统一贸易单据（PI / SO / CI / PL）的「前缀-年份-3位序号」编号规则。
 * <p>无状态工具类，只承担<b>格式化</b>纯逻辑，不依赖 Spring 容器；
 * 序号的生成已收敛到 {@link com.yunhe.website.common.sequence.SequenceStore}（数据库端原子取号）。</p>
 */
public final class DocNumberGenerator {

    /** 单据号格式：前缀-年份-3位序号，如 YHPI-2026-001 */
    private static final String FORMAT = "%s-%d-%03d";

    private DocNumberGenerator() {
    }

    /** 生成单据号，如 {@code generate("YHPI", 2026, 1)} → "YHPI-2026-001" */
    public static String generate(String prefix, int year, int seq) {
        return String.format(FORMAT, prefix, year, seq);
    }
}
