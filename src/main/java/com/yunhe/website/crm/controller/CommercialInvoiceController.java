package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.validation.ReviseGroup;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.crm.dto.CommercialInvoiceDto;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.request.CommercialInvoiceForm;
import com.yunhe.website.crm.service.CommercialInvoiceService;
import com.yunhe.website.crm.service.DocumentChainService;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 商业发票控制器。
 */
@Controller
@RequestMapping("/crm/commercial-invoices")
@RequiredArgsConstructor
public class CommercialInvoiceController {

    private final CommercialInvoiceService invoiceService;
    private final DocumentChainService documentChainService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_LIST)")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<CommercialInvoiceDto> invoices = invoiceService.list(PageUtil.of(page, size));
        model.addAttribute("invoices", invoices);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, invoices.getTotalPages()));
        return "commercial-invoice/list";
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_CREATE)")
    public String generateFromPi(@RequestParam Long piId, RedirectAttributes redirectAttributes) {
        try {
            CommercialInvoiceDto dto = invoiceService.generateFromPi(piId);
            redirectAttributes.addFlashAttribute("successMessage", "商业发票创建成功：" + dto.invoiceNo());
            return "redirect:/crm/commercial-invoices/" + dto.id() + "/view";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/crm/proforma-invoices/" + piId + "/view";
        }
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_LIST)")
    public String view(@PathVariable Long id, Model model) {
        CommercialInvoiceDto invoice = invoiceService.getById(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("versions", invoiceService.listVersions(id));
        if (invoice.rootQuotationId() != null) {
            model.addAttribute("chain", documentChainService.buildChain(invoice.rootQuotationId()));
        }
        return "commercial-invoice/detail";
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_UPDATE)")
    public String generate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        invoiceService.generate(id);
        redirectAttributes.addFlashAttribute("successMessage", "商业发票已生成");
        return "redirect:/crm/commercial-invoices/" + id + "/view";
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_LIST)")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long id, @PathVariable Long versionId) {
        VersionFileDto file = invoiceService.getVersionFile(id, versionId);
        if (file.pdf() == null || file.pdf().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.pdf());
    }

    @GetMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_UPDATE)")
    public String reviseForm(@PathVariable Long id, Model model) {
        model.addAttribute("commercialInvoiceForm", toForm(invoiceService.getById(id)));
        model.addAttribute("isEdit", true);
        model.addAttribute("isChange", true);
        return "commercial-invoice/form";
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_UPDATE)")
    public String revise(@PathVariable Long id,
                         @Validated({Default.class, ReviseGroup.class}) @ModelAttribute("commercialInvoiceForm") CommercialInvoiceForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareReviseModel(model);
            return "commercial-invoice/form";
        }
        try {
            CommercialInvoiceDto dto = invoiceService.revise(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "商业发票变更成功");
            return "redirect:/crm/commercial-invoices/" + dto.id() + "/view";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareReviseModel(model);
            return "commercial-invoice/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("commercialInvoiceForm", toForm(invoiceService.getById(id)));
        model.addAttribute("isEdit", true);
        return "commercial-invoice/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("commercialInvoiceForm") CommercialInvoiceForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "commercial-invoice/form";
        }
        try {
            invoiceService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "commercial-invoice/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "商业发票更新成功");
        return "redirect:/crm/commercial-invoices/" + id + "/view";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).COMMERCIAL_INVOICE_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "商业发票删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/commercial-invoices";
    }

    private void prepareReviseModel(Model model) {
        model.addAttribute("isEdit", true);
        model.addAttribute("isChange", true);
    }

    private CommercialInvoiceForm toForm(CommercialInvoiceDto dto) {
        CommercialInvoiceForm form = new CommercialInvoiceForm();
        form.setId(dto.id());
        form.setInvoiceDate(dto.invoiceDate());
        form.setRemark(dto.remark());
        form.setDepositPercentage(dto.depositPercentage());
        form.setDepositPaymentMethod(dto.depositPaymentMethod());
        form.setBalancePaymentMethod(dto.balancePaymentMethod());
        return form;
    }
}
