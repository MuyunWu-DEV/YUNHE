package com.yunhe.website.crm.mapper;

import com.yunhe.website.crm.dto.CustomerDto;
import com.yunhe.website.crm.dto.CustomerFileDto;
import com.yunhe.website.crm.entity.Customer;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 客户实体与 DTO 映射器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CustomerMapper {

    /** 列表用：不含附件 */
    @Mapping(target = "files", ignore = true)
    CustomerDto toDto(Customer customer);

    /** 详情用：附带附件元数据 */
    @Mapping(target = "files", source = "files")
    CustomerDto toDetailDto(Customer customer, List<CustomerFileDto> files);
}
