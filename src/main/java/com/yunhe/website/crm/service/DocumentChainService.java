package com.yunhe.website.crm.service;

import com.yunhe.website.crm.dto.CommercialInvoiceDto;
import com.yunhe.website.crm.dto.DocumentChainDto;
import com.yunhe.website.crm.dto.PackingListDto;
import com.yunhe.website.crm.dto.QuotationDto;
import com.yunhe.website.crm.dto.ProformaInvoiceDto;
import com.yunhe.website.crm.dto.SalesOrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单据链溯源服务：以报价单为根聚合其全部下游单据。
 */
@Service
@RequiredArgsConstructor
public class DocumentChainService {

    private final QuotationService quotationService;
    private final ProformaInvoiceService proformaInvoiceService;
    private final SalesOrderService salesOrderService;
    private final CommercialInvoiceService commercialInvoiceService;
    private final PackingListService packingListService;

    /** 以报价单为根构建单据链 */
    @Transactional(readOnly = true)
    public DocumentChainDto buildChain(Long quotationId) {
        QuotationDto quotation = quotationService.getById(quotationId);
        ProformaInvoiceDto pi = proformaInvoiceService.getByQuotationId(quotationId);
        SalesOrderDto order = salesOrderService.getByRootQuotationId(quotationId);
        CommercialInvoiceDto ci = commercialInvoiceService.getByRootQuotationId(quotationId);
        PackingListDto pl = packingListService.getByRootQuotationId(quotationId);
        return new DocumentChainDto(quotation, pi, order, ci, pl);
    }
}
