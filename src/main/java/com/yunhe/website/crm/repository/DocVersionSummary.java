package com.yunhe.website.crm.repository;

import java.time.Instant;

/**
 * 单据版本「列表」轻量投影：版本历史列表只需展示 id / versionNo / createdAt / log。
 * <p>三个版本仓储（CommercialInvoice / ProformaInvoice / PackingList）的列表查询返回本投影，
 * 从而只 select 这四列，避免整行加载 LONGBLOB(pdf) 与 LONGTEXT(data) 大字段导致的巨额传输与内存开销。</p>
 */
public interface DocVersionSummary {

    Long getId();

    int getVersionNo();

    Instant getCreatedAt();

    String getLog();
}
