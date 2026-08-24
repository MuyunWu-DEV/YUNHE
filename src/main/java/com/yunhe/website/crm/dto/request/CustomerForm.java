package com.yunhe.website.crm.dto.request;

import com.yunhe.website.crm.entity.CustomerPriority;
import com.yunhe.website.crm.entity.CustomerTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 客户创建 / 编辑表单 DTO。
 */
@Data
public class CustomerForm {

    private Long id;

    @NotBlank(message = "客户姓名不能为空")
    @Size(max = 50, message = "客户姓名不能超过 50 个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 100, message = "公司不能超过 100 个字符")
    private String company;

    @Size(max = 50, message = "注册号不能超过 50 个字符")
    private String registrationNo;

    @Size(max = 255, message = "地址不能超过 255 个字符")
    private String address;

    /** 客户标签（多选） */
    private List<CustomerTag> tags = new ArrayList<>();

    private CustomerPriority priority = CustomerPriority.MEDIUM;

    /** 下次跟进时间（到天） */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextFollowUpAt;
}
