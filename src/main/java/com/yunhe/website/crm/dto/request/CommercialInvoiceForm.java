package com.yunhe.website.crm.dto.request;

import com.yunhe.website.common.validation.ReviseGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 商业发票编辑表单 DTO（商业发票由 PI 生成，此表单仅用于编辑日期/备注）。
 */
@Data
public class CommercialInvoiceForm {

    private Long id;

    @NotNull(message = "发票日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate invoiceDate;

    private String remark;

    /** 定金百分比（0-100） */
    @DecimalMin(value = "0", message = "定金百分比不能小于 0")
    @DecimalMax(value = "100", message = "定金百分比不能大于 100")
    private BigDecimal depositPercentage;

    /** 定金付款方式 */
    private String depositPaymentMethod;

    /** 尾款付款方式 */
    private String balancePaymentMethod;

    /** 变更原因（仅「发起变更」时必填，由 ReviseGroup 分组校验） */
    @NotBlank(groups = ReviseGroup.class, message = "变更原因不能为空")
    private String changeReason;
}
