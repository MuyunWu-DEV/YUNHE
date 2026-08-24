package com.yunhe.website.crm.mapper;

import com.yunhe.website.crm.dto.QuotationDto;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.Quotation;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 报价单实体与 DTO 映射器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface QuotationMapper {

    QuotationDto toDto(Quotation quotation);

    QuotationDto.CustomerSummary toCustomerSummary(Customer customer);
}
