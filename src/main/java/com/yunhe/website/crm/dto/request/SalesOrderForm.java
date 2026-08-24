package com.yunhe.website.crm.dto.request;

import com.yunhe.website.crm.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 销售订单编辑表单 DTO（订单只能由 PI 转换而来，此表单仅用于编辑日期/状态/备注）。
 */
@Data
public class SalesOrderForm {

    private Long id;

    @NotNull(message = "订单日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate orderDate;

    private OrderStatus status = OrderStatus.DRAFT;

    private String remark;
}
