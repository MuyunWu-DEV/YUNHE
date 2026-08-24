package com.yunhe.website.crm.dto.request;

import com.yunhe.website.common.validation.ReviseGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 报价单创建 / 编辑表单 DTO。
 */
@Data
public class QuotationForm {

    private Long id;

    @NotNull(message = "报价日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate quoteDate;

    private String remark;

    /** 变更原因（仅「发起变更」时必填，由 ReviseGroup 分组校验） */
    @NotBlank(groups = ReviseGroup.class, message = "变更原因不能为空")
    private String changeReason;

    /** 关联客户 ID */
    private Long customerId;

    /** 明细：品名 → 明细列表，默认一个空分组，页面直接展示无需点添加 */
    @Valid
    private List<DetailGroupForm> details = new ArrayList<>(List.of(new DetailGroupForm()));

    /**
     * 品名分组。
     */
    @Data
    public static class DetailGroupForm {

        private String productName;

        private String hsCode;

        @Valid
        private List<DetailItemForm> items = new ArrayList<>(List.of(new DetailItemForm()));
    }

    /**
     * 明细项。
     */
    @Data
    public static class DetailItemForm {

        private String description;

        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "1", message = "单价必须大于 0")
        private BigDecimal unitPrice;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于 0")
        private Integer quantity;

        private String unit;

        private String currency;
    }
}
