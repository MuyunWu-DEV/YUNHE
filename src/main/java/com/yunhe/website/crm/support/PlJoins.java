package com.yunhe.website.crm.support;

import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 装箱单（PL）的「报价单项 ↔ 装箱行」JOIN 与合计工具。
 *
 * <p><b>单一数据源 = 报价单</b>：装箱行（{@link PackingLine}）只存装箱专属数据（件数 / 净重 / 毛重 / 体积），
 * 货物字段（品名 / HS Code / 描述 / 数量）由 {@link QuoteDetailItem#key()} 经 {@link PackingLine#quoteLineKey()}
 * JOIN 取得。本类把"扁平化报价单项 / 按 key 建索引 / 按 key 关联装箱行 / 合计"集中在一处，
 * 供 Web 详情页、编辑表单、PDF 渲染共用，从而根除 {@code key() vs quoteLineKey()} 不对称带来的脚枪。</p>
 *
 * <p>入参统一取报价单的 {@code List<QuoteDetailGroup>}（实体与 {@code QuotationDto.details()} 共用同一实体类型），
 * 从而同时兼容 Web 链（{@code QuotationDto}）与 PDF 渲染（实体 {@code Quotation}）两条路径，无需关心外层包装。</p>
 */
public final class PlJoins {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private PlJoins() {
    }

    /** 报价单项 + 其所属分组上下文（组名 / HS Code） */
    public record QuoteItemRef(String groupName, String hsCode, QuoteDetailItem item) {
    }

    /** 渲染层 JOIN 结果：一行 = 一个报价单项 + 其对应装箱行（按 key 命中，无位置回退） */
    public record JoinedRow(
            String groupName,
            String hsCode,
            QuoteDetailItem item,
            PackingLineLike line,
            boolean firstInGroup,
            int groupItemCount
    ) {
    }

    /** 合计（仅 key 命中的装箱行参与；与 Web 详情页口径一致） */
    public record PlTotals(int packages, BigDecimal net, BigDecimal gross, BigDecimal vol, int quantity) {
    }

    /** 报价单项扁平化（保留所属分组名 / HS Code），按原始顺序 */
    public static List<QuoteItemRef> flatten(List<QuoteDetailGroup> details) {
        List<QuoteItemRef> out = new ArrayList<>();
        if (details == null) {
            return out;
        }
        for (QuoteDetailGroup g : details) {
            if (g.items() == null) {
                continue;
            }
            for (QuoteDetailItem it : g.items()) {
                out.add(new QuoteItemRef(g.name(), g.hsCode(), it));
            }
        }
        return out;
    }

    /** 报价单项按 item.key() 建索引（有序，重复 key 仅保留首条） */
    public static Map<String, QuoteItemRef> indexByKey(List<QuoteDetailGroup> details) {
        Map<String, QuoteItemRef> map = new LinkedHashMap<>();
        for (QuoteItemRef ref : flatten(details)) {
            if (ref.item().key() != null) {
                map.putIfAbsent(ref.item().key(), ref);
            }
        }
        return map;
    }

    /**
     * 按报价单项遍历，将每个报价单项与其命中 key 的装箱行配对（{@link PackingLine#quoteLineKey()} == {@link QuoteDetailItem#key()}）。
     * 未命中则 line = null（显示 -），不做位置回退。孤儿装箱行（key 不在报价单）被丢弃。
     */
    public static List<JoinedRow> join(List<? extends PackingLineLike> lines, List<QuoteDetailGroup> details) {
        List<JoinedRow> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        Map<String, PackingLineLike> byKey = new LinkedHashMap<>();
        for (PackingLineLike l : lines) {
            if (l.quoteLineKey() != null && !l.quoteLineKey().isBlank()) {
                byKey.putIfAbsent(l.quoteLineKey(), l);
            }
        }
        if (details == null) {
            return result;
        }
        for (QuoteDetailGroup g : details) {
            List<QuoteDetailItem> items = g.items() != null ? g.items() : List.of();
            int count = items.size();
            for (int i = 0; i < count; i++) {
                QuoteDetailItem item = items.get(i);
                PackingLineLike line = item.key() != null ? byKey.get(item.key()) : null;
                result.add(new JoinedRow(g.name(), g.hsCode(), item, line, i == 0, count));
            }
        }
        return result;
    }

    /** 仅计 key 命中行（line.quoteLineKey() == item.key()），去重后求和；无命中 → 全 0 */
    public static PlTotals totals(List<JoinedRow> rows) {
        int pkgs = 0;
        int qty = 0;
        BigDecimal net = ZERO;
        BigDecimal gross = ZERO;
        BigDecimal vol = ZERO;
        for (JoinedRow r : rows) {
            if (r.item() != null) {
                qty += r.item().quantity();
            }
            if (r.line() != null && r.item() != null
                    && r.line().quoteLineKey() != null
                    && r.line().quoteLineKey().equals(r.item().key())) {
                if (r.line().packages() != null) {
                    pkgs += r.line().packages();
                }
                if (r.line().netWeight() != null) {
                    net = net.add(r.line().netWeight());
                }
                if (r.line().grossWeight() != null) {
                    gross = gross.add(r.line().grossWeight());
                }
                if (r.line().measurement() != null) {
                    vol = vol.add(r.line().measurement());
                }
            }
        }
        return new PlTotals(pkgs, net, gross, vol, qty);
    }
}
