package com.yunhe.website.crm.service;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉死 {@link PackingListService#resyncLinesToQuotation} 的「严格按 key 对齐」语义：
 * <ul>
 *   <li>key 命中 → 保留既有装箱值（直接更新/复用）；</li>
 *   <li>报价单新增项（key 不在 PL）→ 插入空行；</li>
 *   <li>PL 孤儿行（key 不在报价单）→ 移除；</li>
 *   <li>无报价单 → 不改变既有行；空 PL 行 → 按报价单项全量播种空行。</li>
 * </ul>
 * 取代历史的位置 1:1 重锚，单一来源 = 报价单。
 */
class PlResyncTest {

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

    private PackingLine line(String key, int pkgs) {
        return new PackingLine(key, pkgs, new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("0.1"));
    }

    @Test
    void keyHitKeepsValues_newKeyInserted_orphanRemoved() {
        Quotation q = quotationWith("k1", "k2", "k3");
        PackingList pl = new PackingList();
        // 存量：k1/k2 已填装箱值；k-old 为报价单已无的孤儿行
        pl.setLines(new ArrayList<>(List.of(
                line("k1", 2),
                line("k2", 3),
                line("k-old", 99))));

        PackingListService.resyncLinesToQuotation(pl, q);

        List<PackingLine> resynced = pl.getLines();
        // 顺序与数量跟随报价单
        assertEquals(3, resynced.size());
        assertEquals("k1", resynced.get(0).quoteLineKey());
        assertEquals("k2", resynced.get(1).quoteLineKey());
        assertEquals("k3", resynced.get(2).quoteLineKey());
        // key 命中：保留已填装箱值
        assertEquals(2, resynced.get(0).packages());
        assertEquals(3, resynced.get(1).packages());
        // 报价单新增项 k3：空行
        assertNull(resynced.get(2).packages());
        assertNull(resynced.get(2).netWeight());
        // 孤儿行 k-old 已被移除
        assertTrue(resynced.stream().noneMatch(l -> "k-old".equals(l.quoteLineKey())));
    }

    @Test
    void emptyPlLines_seededFromQuotation() {
        Quotation q = quotationWith("a", "b");
        PackingList pl = new PackingList();
        pl.setLines(new ArrayList<>());

        PackingListService.resyncLinesToQuotation(pl, q);

        List<PackingLine> resynced = pl.getLines();
        assertEquals(2, resynced.size());
        assertEquals("a", resynced.get(0).quoteLineKey());
        assertEquals("b", resynced.get(1).quoteLineKey());
        assertNull(resynced.get(0).packages());
        assertNull(resynced.get(1).packages());
    }

    @Test
    void noQuotation_noOp() {
        PackingList pl = new PackingList();
        pl.setLines(new ArrayList<>(List.of(line("x", 5))));

        PackingListService.resyncLinesToQuotation(pl, null);

        // 报价单为空：不改变既有行
        assertEquals(1, pl.getLines().size());
        assertEquals("x", pl.getLines().get(0).quoteLineKey());
    }
}
