package com.yunhe.website.crm.support;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FormatUtil} 纯单元测试：锁定「PDF 单据 fmtMoney 与 Web 金额展示共享同一实现」后，
 * moneyGrouped 必须与旧 PDF fmtMoney（String.format %,d / %,.2f, Locale.US）输出完全一致。
 * 这里在测试内联旧逻辑作为参照，防止后续任一实现分叉导致 PDF 金额走样。
 */
class FormatUtilTest {

    /** 旧 PDF fmtMoney 实现（AbstractTradePdfRenderer 委托 moneyGrouped 前的原始逻辑，作参照） */
    private static String legacyFmtMoney(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() <= 0) {
            return String.format(Locale.US, "%,d", s.toBigInteger());
        }
        return String.format(Locale.US, "%,.2f", v);
    }

    @Test
    void moneyGroupedMatchesLegacyPdfFmtMoney() {
        BigDecimal[] values = {
                BigDecimal.ZERO,                       // 0
                new BigDecimal("100"),                 // 整数
                new BigDecimal("999999"),              // 千分位整数
                new BigDecimal("1234567"),             // 千分位整数多段
                new BigDecimal("100.00"),              // stripTrailingZeros → 整数
                new BigDecimal("1234.5"),              // 到分补零 1,234.50
                new BigDecimal("1234.56"),
                new BigDecimal("9999999.9"),           // 千分位 + 到分补零
                new BigDecimal("0.5"),
                new BigDecimal("0.05"),
                new BigDecimal("100000000.00"),        // 大额到分
        };
        for (BigDecimal v : values) {
            assertThat(FormatUtil.moneyGrouped(v))
                    .as("moneyGrouped(%s)", v)
                    .isEqualTo(legacyFmtMoney(v));
        }
    }

    @Test
    void moneyGroupedFormatting() {
        assertThat(FormatUtil.moneyGrouped(new BigDecimal("1234567.8"))).isEqualTo("1,234,567.80");
        assertThat(FormatUtil.moneyGrouped(new BigDecimal("1234567"))).isEqualTo("1,234,567");
        assertThat(FormatUtil.moneyGrouped(new BigDecimal("100"))).isEqualTo("100");
        assertThat(FormatUtil.moneyGrouped(null)).isEqualTo("-");
        assertThat(FormatUtil.moneyGrouped(BigDecimal.ZERO)).isEqualTo("0");
    }

    @Test
    void existingMoneyWeightUnchanged() {
        // 既有 Web 端方法行为不被 C1b 破坏
        assertThat(FormatUtil.money(new BigDecimal("1234.50"))).isEqualTo("1234.5");   // 无尾随0
        assertThat(FormatUtil.money(new BigDecimal("1234"))).isEqualTo("1234");        // 整数无 .00
        assertThat(FormatUtil.money(null)).isEqualTo("-");
        assertThat(FormatUtil.weight(new BigDecimal("100.500"))).isEqualTo("100.5");
        assertThat(FormatUtil.weight(new BigDecimal("12.1234"))).isEqualTo("12.123");  // 最多3位
        assertThat(FormatUtil.weight(null)).isEqualTo("-");
    }
}
