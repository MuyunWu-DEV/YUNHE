package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.validation.ReviseGroup;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.crm.dto.CustomerDto;
import com.yunhe.website.crm.dto.ProformaInvoiceDto;
import com.yunhe.website.crm.dto.QuotationDto;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.request.ProformaInvoiceForm;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.CrmTermsLib;
import com.yunhe.website.crm.service.CustomerService;
import com.yunhe.website.crm.service.DocumentChainService;
import com.yunhe.website.crm.service.ProformaInvoiceService;
import com.yunhe.website.crm.service.QuotationService;
import com.yunhe.website.crm.service.CrmTermsLibService;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 形式发票控制器。
 */
@Controller
@RequestMapping("/crm/proforma-invoices")
@RequiredArgsConstructor
public class ProformaInvoiceController {

    private final ProformaInvoiceService invoiceService;
    private final QuotationService quotationService;
    private final CustomerService customerService;
    private final CrmTermsLibService termsLibService;
    private final DocumentChainService documentChainService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_LIST)")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<ProformaInvoiceDto> invoices = invoiceService.list(PageUtil.of(page, size));
        model.addAttribute("invoices", invoices);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, invoices.getTotalPages()));
        return "proforma-invoice/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_CREATE)")
    public String createForm(@RequestParam(required = false) Long quotationId, Model model) {
        ProformaInvoiceForm form = new ProformaInvoiceForm();
        form.setInvoiceDate(LocalDate.now());
        prefillFromSettings(form);

        // 从报价单自动加载 buyer 信息
        if (quotationId != null) {
            form.setQuotationId(quotationId);
            QuotationDto quotation = quotationService.getById(quotationId);
            if (quotation.customer() != null) {
                Long customerId = quotation.customer().id();
                form.setCustomerId(customerId);
                CustomerDto customer = customerService.getById(customerId);
                form.setBuyerCompanyName(customer.company());
                form.setBuyerRegistrationNo(customer.registrationNo());
                form.setBuyerAddress(customer.address());
            }
        }

        model.addAttribute("proformaInvoiceForm", form);
        prepareFormModel(model, false);
        return "proforma-invoice/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_CREATE)")
    public String create(@Valid @ModelAttribute("proformaInvoiceForm") ProformaInvoiceForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, false);
            return "proforma-invoice/form";
        }
        try {
            ProformaInvoiceDto dto = invoiceService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "形式发票创建成功：" + dto.invoiceNumber());
            return "redirect:/crm/proforma-invoices/" + dto.id() + "/view";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, false);
            return "proforma-invoice/form";
        }
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_LIST)")
    public String view(@PathVariable Long id, Model model) {
        ProformaInvoiceDto invoice = invoiceService.getById(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("versions", invoiceService.listVersions(id));
        if (invoice.quotationId() != null) {
            model.addAttribute("chain", documentChainService.buildChain(invoice.quotationId()));
        }
        return "proforma-invoice/detail";
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_UPDATE)")
    public String generate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        invoiceService.generate(id);
        redirectAttributes.addFlashAttribute("successMessage", "形式发票已生成");
        return "redirect:/crm/proforma-invoices/" + id + "/view";
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_LIST)")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long versionId) {
        VersionFileDto file = invoiceService.getVersionFile(versionId);
        if (file.pdf() == null || file.pdf().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.pdf());
    }

    @GetMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_UPDATE)")
    public String reviseForm(@PathVariable Long id, Model model) {
        model.addAttribute("proformaInvoiceForm", toForm(invoiceService.getById(id)));
        prepareFormModel(model, true);
        model.addAttribute("isChange", true);
        return "proforma-invoice/form";
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_UPDATE)")
    public String revise(@PathVariable Long id,
                         @Validated({Default.class, ReviseGroup.class}) @ModelAttribute("proformaInvoiceForm") ProformaInvoiceForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareReviseModel(model);
            return "proforma-invoice/form";
        }
        try {
            ProformaInvoiceDto dto = invoiceService.revise(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "形式发票变更成功");
            return "redirect:/crm/proforma-invoices/" + dto.id() + "/view";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareReviseModel(model);
            return "proforma-invoice/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("proformaInvoiceForm", toForm(invoiceService.getById(id)));
        prepareFormModel(model, true);
        return "proforma-invoice/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("proformaInvoiceForm") ProformaInvoiceForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, true);
            return "proforma-invoice/form";
        }
        try {
            ProformaInvoiceDto dto = invoiceService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "形式发票更新成功");
            return "redirect:/crm/proforma-invoices/" + dto.id() + "/view";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, true);
            return "proforma-invoice/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PROFORMA_INVOICE_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "形式发票删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/proforma-invoices";
    }

    private void prepareFormModel(Model model, boolean isEdit) {
        List<CrmTermsLib> termsLibs = termsLibService.list();
        model.addAttribute("termsLibs", termsLibs);
        Map<Long, Map<String, String>> prefills = new LinkedHashMap<>();
        for (CrmTermsLib lib : termsLibs) {
            prefills.put(lib.getId(), termsLibService.prefillMapOf(lib));
        }
        model.addAttribute("termsLibPrefills", prefills);
        model.addAttribute("isEdit", isEdit);
        // 新增模式（非编辑/非变更）下，进入页面默认选中并导入第一个条款库
        model.addAttribute("autoImportTermsLib", !isEdit);
    }

    private void prepareReviseModel(Model model) {
        prepareFormModel(model, true);
        model.addAttribute("isChange", true);
    }

    private void prefillFromSettings(ProformaInvoiceForm form) {
        CrmTermsLib lib = termsLibService.get();
        form.setTermsLibId(lib.getId());
        form.setSellerCompanyName(lib.getCompanyNameEnglish());
        form.setSellerAddress(lib.getAddress());
        form.setSellerPhone(lib.getPhone());
        form.setSellerEmail(lib.getEmail());
        form.setTerms(termsLibService.getTerms());
        form.setBankAccountInformation(termsLibService.getBankAccountInformation());
    }

    private ProformaInvoiceForm toForm(ProformaInvoiceDto dto) {
        ProformaInvoiceForm form = new ProformaInvoiceForm();
        form.setId(dto.id());
        form.setInvoiceNumber(dto.invoiceNumber());
        form.setInvoiceDate(dto.invoiceDate());
        form.setCustomerId(dto.customer() == null ? null : dto.customer().id());
        form.setQuotationId(dto.quotationId());
        ProformaDetails details = dto.details();
        if (details != null) {
            if (details.seller() != null) {
                form.setSellerCompanyName(details.seller().companyName());
                form.setSellerAddress(details.seller().address());
                form.setSellerPhone(details.seller().phone());
                form.setSellerEmail(details.seller().email());
            }
            if (details.buyer() != null) {
                form.setBuyerCompanyName(details.buyer().companyName());
                form.setBuyerRegistrationNo(details.buyer().registrationNo());
                form.setBuyerAddress(details.buyer().address());
            }
            form.setIncoterms(details.incoterms());
            form.setTerms(details.terms());
            form.setBankAccountInformation(details.bankAccountInformation());
            form.setWarranty(details.warranty());
        }
        return form;
    }
}
