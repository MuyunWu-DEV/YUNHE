package com.yunhe.website.crm.dto.request;

import com.yunhe.website.common.validation.ReviseGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 装箱单编辑表单 DTO（装箱单由 PI 生成，此表单用于填写逐货物项装箱信息）。
 * <p>整单合计（件数/毛重/净重/体积）不再由用户填写，统一由 {@link #lines} 求和派生。</p>
 */
@Data
public class PackingListForm {

    private Long id;

    @NotNull(message = "装箱日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate packingDate;

    private String marks;

    /** 逐货物项装箱信息（与报价单项经 quoteLineKey 一一对应） */
    private List<PackingLineForm> lines = new ArrayList<>();

    private String remark;

    /** 变更原因（仅「发起变更」时必填，由 ReviseGroup 分组校验） */
    @NotBlank(groups = ReviseGroup.class, message = "变更原因不能为空")
    private String changeReason;
}
