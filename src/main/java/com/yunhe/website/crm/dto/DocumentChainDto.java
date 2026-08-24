package com.yunhe.website.crm.dto;

/**
 * 单据链视图：以报价单为根，聚合其全部下游单据，实现「从任意一处找回整条链」。
 */
public record DocumentChainDto(
        QuotationDto quotation,
        ProformaInvoiceDto proformaInvoice,
        SalesOrderDto salesOrder,
        CommercialInvoiceDto commercialInvoice,
        PackingListDto packingList
) {
}
