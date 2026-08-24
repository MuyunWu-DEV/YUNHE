package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.crm.dto.SalesOrderDto;
import com.yunhe.website.crm.dto.request.SalesOrderForm;
import com.yunhe.website.crm.service.DocumentChainService;
import com.yunhe.website.crm.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 销售订单控制器。
 */
@Controller
@RequestMapping("/crm/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final DocumentChainService documentChainService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SALES_ORDER_LIST)")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<SalesOrderDto> orders = salesOrderService.list(PageUtil.of(page, size));
        model.addAttribute("orders", orders);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, orders.getTotalPages()));
        return "sales-order/list";
    }

    @PostMapping("/convert")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SALES_ORDER_CREATE)")
    public String convert(@RequestParam Long piId, RedirectAttributes redirectAttributes) {
        try {
            SalesOrderDto dto = salesOrderService.convertFromPi(piId);
            redirectAttributes.addFlashAttribute("successMessage", "订单创建成功：" + dto.orderNo());
            return "redirect:/crm/sales-orders/" + dto.id() + "/view";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/crm/proforma-invoices/" + piId + "/view";
        }
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SALES_ORDER_LIST)")
    public String view(@PathVariable Long id, Model model) {
        SalesOrderDto order = salesOrderService.getById(id);
        model.addAttribute("order", order);
        if (order.rootQuotationId() != null) {
            model.addAttribute("chain", documentChainService.buildChain(order.rootQuotationId()));
        }
        return "sales-order/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SALES_ORDER_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("salesOrderForm", toForm(salesOrderService.getById(id)));
        model.addAttribute("isEdit", true);
        return "sales-order/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SALES_ORDER_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("salesOrderForm") SalesOrderForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "sales-order/form";
        }
        try {
            salesOrderService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "sales-order/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "订单更新成功");
        return "redirect:/crm/sales-orders/" + id + "/view";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SALES_ORDER_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            salesOrderService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "订单删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/sales-orders";
    }

    private SalesOrderForm toForm(SalesOrderDto dto) {
        SalesOrderForm form = new SalesOrderForm();
        form.setId(dto.id());
        form.setOrderDate(dto.orderDate());
        form.setStatus(dto.status());
        form.setRemark(dto.remark());
        return form;
    }
}
