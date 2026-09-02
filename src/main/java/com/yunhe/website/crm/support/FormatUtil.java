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

    /** 重量 / 体积展示：最多 3 位小数、无尾随 0、小数点为 "."、无千分位。null => "-" */
    public static String weight(BigDecimal v) {
        return v == null ? "-" : fmt("0.###").format(v);
    }

    /** 金额展示：最多 2 位小数、无尾随 0、小数点为 "."。null => "-" */
    public static String money(BigDecimal v) {
        return v == null ? "-" : fmt("0.##").format(v);
    }
}
