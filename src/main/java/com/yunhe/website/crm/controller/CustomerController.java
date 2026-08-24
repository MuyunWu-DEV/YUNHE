package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.crm.dto.CustomerDto;
import com.yunhe.website.crm.dto.request.CustomerForm;
import com.yunhe.website.crm.entity.CustomerFile;
import com.yunhe.website.crm.service.CustomerService;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 客户档案控制器。
 */
@Controller
@RequestMapping("/crm/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_LIST)")
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<CustomerDto> customers = customerService.list(keyword, PageUtil.of(page, size));
        model.addAttribute("customers", customers);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, customers.getTotalPages()));
        return "customer/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_CREATE)")
    public String createForm(Model model) {
        model.addAttribute("customerForm", new CustomerForm());
        model.addAttribute("isEdit", false);
        return "customer/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_CREATE)")
    public String create(@Valid @ModelAttribute("customerForm") CustomerForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "customer/form";
        }
        try {
            customerService.create(form, files);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            return "customer/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "客户创建成功");
        return "redirect:/crm/customers";
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_LIST)")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("customer", customerService.getById(id));
        return "customer/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        CustomerDto dto = customerService.getById(id);
        model.addAttribute("customerForm", toForm(dto));
        model.addAttribute("files", dto.files());
        model.addAttribute("isEdit", true);
        return "customer/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("customerForm") CustomerForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareEditModel(id, model, true);
            return "customer/form";
        }
        try {
            customerService.update(id, form, files);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareEditModel(id, model, true);
            return "customer/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "客户更新成功");
        return "redirect:/crm/customers";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            customerService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "客户删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/customers";
    }

    /** 附件下载 */
    @GetMapping("/{id}/files/{fileId}/download")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_LIST)")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id, @PathVariable Long fileId) {
        CustomerFile file = customerService.getFileForDownload(id, fileId);
        String encodedName = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        } catch (InvalidMediaTypeException e) {
            // 上传时客户端可控的 Content-Type 可能非法，兜底为通用二进制流
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(mediaType)
                .body(file.getContent());
    }

    /** 附件删除 */
    @PostMapping("/{id}/files/{fileId}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).CUSTOMER_UPDATE)")
    public String deleteFile(@PathVariable Long id, @PathVariable Long fileId,
                             RedirectAttributes redirectAttributes) {
        try {
            customerService.deleteFile(id, fileId);
            redirectAttributes.addFlashAttribute("successMessage", "附件删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/customers/" + id + "/edit";
    }

    private void prepareEditModel(Long id, Model model, boolean isEdit) {
        model.addAttribute("files", customerService.listFileMetadata(id));
        model.addAttribute("isEdit", isEdit);
    }

    private CustomerForm toForm(CustomerDto dto) {
        CustomerForm form = new CustomerForm();
        form.setId(dto.id());
        form.setName(dto.name());
        form.setPhone(dto.phone());
        form.setCompany(dto.company());
        form.setRegistrationNo(dto.registrationNo());
        form.setAddress(dto.address());
        form.setTags(dto.tags() == null ? new ArrayList<>() : new ArrayList<>(dto.tags()));
        form.setPriority(dto.priority());
        form.setNextFollowUpAt(dto.nextFollowUpAt());
        return form;
    }
}
