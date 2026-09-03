package com.yunhe.website.crm.support;

import com.yunhe.website.crm.entity.PackingLine;
import com.yunhe.website.crm.entity.PackingList;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉死 PL PDF 合计的「仅计 key 命中行」语义：
 * computeKeyMatchedTotals 只累计 line.quoteLineKey() 与对应报价单项 item.key() 一致的行，
 * 排除位置回退行 / 未关联行；无任何命中时合计恒为 0。
 */
class PlPdfTotalsTest {

    private Quotation quotationWith(String... keys) {
        Quotation q = new Quotation();
        List<QuoteDetailItem> items = new ArrayList<>();
        int i = 0;
        for (String k : keys) {
            items.add(new QuoteDetailItem(k, "desc-" + k, BigDecimal.TEN, 10 + i, "SETS", "USD"));
            i++;
        }
        q.setDetails(List.of(new QuoteDetailGroup("G", "84463090", items)));
        return q;
    }

    private PackingLine line(String key, int pkgs, String net, String gross, String vol) {
        return new PackingLine(key, pkgs, new BigDecimal(net), new BigDecimal(gross), new BigDecimal(vol));
    }

    private static void assertTotal(PlJoins.PlTotals t, int pkgs, String net, String gross, String vol) {
        assertEquals(pkgs, t.packages());
        assertEquals(0, new BigDecimal(net).compareTo(t.net()));
        assertEquals(0, new BigDecimal(gross).compareTo(t.gross()));
        assertEquals(0, new BigDecimal(vol).compareTo(t.vol()));
    }

    @Test
    void allMatched_sumsBothLines() {
        Quotation q = quotationWith("qi-1", "qi-2");
        PackingList pl = new PackingList();
        pl.setLines(List.of(
                line("qi-1", 2, "100.000", "110.000", "1.100"),
                line("qi-2", 3, "200.000", "220.000", "2.200")));
        assertTotal(PlJoins.totals(PlJoins.join(pl.getLines(), q.getDetails())),
                5, "300.000", "330.000", "3.300");
    }

    @Test
    void unmatchedKey_excludedFromTotal() {
        Quotation q = quotationWith("qi-1", "qi-2");
        PackingList pl = new PackingList();
        pl.setLines(List.of(
                line("qi-1", 2, "100.000", "110.000", "1.100"),
                line("UNMATCHED", 99, "999.000", "999.000", "9.900")));
        // 只有 qi-1 被计入；UNMATCHED 的 99/999/9.9 必须被排除
        assertTotal(PlJoins.totals(PlJoins.join(pl.getLines(), q.getDetails())),
                2, "100.000", "110.000", "1.100");
    }

    @Test
    void allUnmatched_totalIsZero() {
        Quotation q = quotationWith("qi-1");
        PackingList pl = new PackingList();
        pl.setLines(List.of(line("X1", 5, "50.000", "60.000", "0.500")));
        PlJoins.PlTotals t = PlJoins.totals(PlJoins.join(pl.getLines(), q.getDetails()));
        assertEquals(0, t.packages());
        assertEquals(0, BigDecimal.ZERO.compareTo(t.net()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.gross()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.vol()));
    }

    @Test
    void emptyLines_totalIsZero() {
        Quotation q = quotationWith("qi-1");
        PackingList pl = new PackingList();
        pl.setLines(List.of());
        PlJoins.PlTotals t = PlJoins.totals(PlJoins.join(pl.getLines(), q.getDetails()));
        assertEquals(0, t.packages());
        assertEquals(0, BigDecimal.ZERO.compareTo(t.net()));
    }
}
