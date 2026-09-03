package com.yunhe.website.crm.support;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Thymeleaf {@code #numbers.formatDecimal} in 3.1.x has NO overload with separate
 * min/max fraction digits (the single {@code decimalDigits} is used as BOTH min and
 * max, so padding is unavoidable). These helpers give us "integer => no decimals,
 * has fraction => show actual digits (up to N) with no trailing-zero padding",
 * using an explicit US locale so the decimal point is always ".".
 */
public final class FormatUtil {

    private FormatUtil() {
    }

    private static DecimalFormat fmt(String pattern) {
        return new DecimalFormat(pattern, new DecimalFormatSymbols(Locale.US));
    }

    // 千分位金额：与 PDF 单据 fmtMoney 同口径（显式 Locale.US、整数无小数、有小数补到分），预建避免每次 new
    private static final DecimalFormat GROUPED_INT = fmt("#,##0");
    private static final DecimalFormat GROUPED_CENTS = fmt("#,##0.00");

    /** 重量 / 体积展示：最多 3 位小数、无尾随 0、小数点为 "."、无千分位。null => "-" */
    public static String weight(BigDecimal v) {
        return v == null ? "-" : fmt("0.###").format(v);
    }

    /** 金额展示：最多 2 位小数、无尾随 0、小数点为 "."、无千分位（Web 列表明细用）。null => "-" */
    public static String money(BigDecimal v) {
        return v == null ? "-" : fmt("0.##").format(v);
    }

    /**
     * 金额展示（带千分位 + 到分，单据/合计区用，与 PDF fmtMoney 完全同口径）：
     * 整数 => 千分位整数如 {@code 1,234,567}；有小数 => 千分位补到 2 位如 {@code 1,234,567.80}。
     * 显式 Locale.US，小数点恒为 "."。null => "-"。
     */
    public static String moneyGrouped(BigDecimal v) {
        if (v == null) {
            return "-";
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() <= 0 ? GROUPED_INT.format(s.toBigInteger()) : GROUPED_CENTS.format(v);
    }
}
