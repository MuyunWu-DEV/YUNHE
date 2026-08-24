package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.validation.ReviseGroup;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.crm.dto.QuotationDto;
import com.yunhe.website.crm.dto.request.QuotationForm;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.service.CustomerService;
import com.yunhe.website.crm.service.DocumentChainService;
import com.yunhe.website.crm.service.QuotationService;
import com.yunhe.website.security.auth.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 报价管理控制器。
 */
@Controller
@RequestMapping("/crm/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;
    private final CustomerService customerService;
    private final DocumentChainService documentChainService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_LIST)")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<QuotationDto> quotations = quotationService.list(PageUtil.of(page, size));
        model.addAttribute("quotations", quotations);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, quotations.getTotalPages()));
        return "quotation/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_CREATE)")
    public String createForm(Model model) {
        model.addAttribute("quotationForm", new QuotationForm());
        prepareFormModel(model, false);
        return "quotation/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_CREATE)")
    public String create(@Valid @ModelAttribute("quotationForm") QuotationForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, false);
            return "quotation/form";
        }
        try {
            quotationService.create(form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, false);
            return "quotation/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "报价单创建成功");
        return "redirect:/crm/quotations";
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_LIST)")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("quotation", quotationService.getById(id));
        model.addAttribute("chain", documentChainService.buildChain(id));
        model.addAttribute("logs", quotationService.listLogs(id));
        return "quotation/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("quotationForm", toForm(quotationService.getById(id)));
        prepareFormModel(model, true);
        return "quotation/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("quotationForm") QuotationForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, true);
            return "quotation/form";
        }
        try {
            quotationService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, true);
            return "quotation/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "报价单更新成功");
        return "redirect:/crm/quotations";
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_UPDATE)")
    public String send(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        boolean sent = quotationService.markSent(id);
        if (sent) {
            redirectAttributes.addFlashAttribute("successMessage", "报价单已发送");
        } else {
            redirectAttributes.addFlashAttribute("infoMessage", "下载功能开发中，敬请期待");
        }
        return "redirect:/crm/quotations";
    }

    @GetMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_UPDATE)")
    public String reviseForm(@PathVariable Long id, Model model) {
        model.addAttribute("quotationForm", toForm(quotationService.getById(id)));
        prepareFormModel(model, true);
        model.addAttribute("isChange", true);
        return "quotation/form";
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_UPDATE)")
    public String revise(@PathVariable Long id,
                         @Validated({Default.class, ReviseGroup.class}) @ModelAttribute("quotationForm") QuotationForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes,
                         @AuthenticationPrincipal CustomUserDetails user) {
        if (bindingResult.hasErrors()) {
            prepareReviseModel(model);
            return "quotation/form";
        }
        try {
            quotationService.revise(id, form, user.getUsername());
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareReviseModel(model);
            return "quotation/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "报价单变更成功");
        return "redirect:/crm/quotations";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).QUOTATION_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            quotationService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "报价单删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/quotations";
    }

    private void prepareFormModel(Model model, boolean isEdit) {
        model.addAttribute("customers", customerService.listForSelect());
        model.addAttribute("isEdit", isEdit);
    }

    private void prepareReviseModel(Model model) {
        prepareFormModel(model, true);
        model.addAttribute("isChange", true);
    }

    private QuotationForm toForm(QuotationDto dto) {
        QuotationForm form = new QuotationForm();
        form.setId(dto.id());
        form.setQuoteDate(dto.quoteDate());
        form.setRemark(dto.remark());
        form.setCustomerId(dto.customer() == null ? null : dto.customer().id());

        List<QuotationForm.DetailGroupForm> groups = new ArrayList<>();
        if (dto.details() != null) {
            for (var group : dto.details()) {
                QuotationForm.DetailGroupForm groupForm = new QuotationForm.DetailGroupForm();
                groupForm.setProductName(group.name());
                groupForm.setHsCode(group.hsCode());
                groupForm.setItems(toItemForms(group.items()));
                groups.add(groupForm);
            }
        }
        if (!groups.isEmpty()) {
            form.setDetails(groups);
        }
        return form;
    }

    private List<QuotationForm.DetailItemForm> toItemForms(List<QuoteDetailItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        List<QuotationForm.DetailItemForm> result = new ArrayList<>();
        for (QuoteDetailItem item : items) {
            QuotationForm.DetailItemForm itemForm = new QuotationForm.DetailItemForm();
            itemForm.setDescription(item.description());
            itemForm.setUnitPrice(item.unitPrice());
            itemForm.setQuantity(item.quantity());
            itemForm.setUnit(item.unit());
            itemForm.setCurrency(item.currency());
            result.add(itemForm);
        }
        return result;
    }
}
