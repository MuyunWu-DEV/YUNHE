package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.validation.ReviseGroup;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.crm.dto.PackingListDto;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.request.PackingListForm;
import com.yunhe.website.crm.service.DocumentChainService;
import com.yunhe.website.crm.service.PackingListService;
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
 * 装箱单控制器。
 */
@Controller
@RequestMapping("/crm/packing-lists")
@RequiredArgsConstructor
public class PackingListController {

    private final PackingListService packingListService;
    private final DocumentChainService documentChainService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_LIST)")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<PackingListDto> packingLists = packingListService.list(PageUtil.of(page, size));
        model.addAttribute("packingLists", packingLists);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, packingLists.getTotalPages()));
        return "packing-list/list";
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_CREATE)")
    public String generateFromPi(@RequestParam Long piId, RedirectAttributes redirectAttributes) {
        try {
            PackingListDto dto = packingListService.generateFromPi(piId);
            redirectAttributes.addFlashAttribute("successMessage", "装箱单创建成功：" + dto.packingNo());
            return "redirect:/crm/packing-lists/" + dto.id() + "/edit";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/crm/proforma-invoices/" + piId + "/view";
        }
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_LIST)")
    public String view(@PathVariable Long id, Model model) {
        PackingListDto packingList = packingListService.getById(id);
        model.addAttribute("packingList", packingList);
        model.addAttribute("versions", packingListService.listVersions(id));
        if (packingList.rootQuotationId() != null) {
            model.addAttribute("chain", documentChainService.buildChain(packingList.rootQuotationId()));
        }
        return "packing-list/detail";
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String generate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        packingListService.generate(id);
        redirectAttributes.addFlashAttribute("successMessage", "装箱单已生成");
        return "redirect:/crm/packing-lists/" + id + "/view";
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_LIST)")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long versionId) {
        VersionFileDto file = packingListService.getVersionFile(versionId);
        if (file.pdf() == null || file.pdf().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.pdf());
    }

    @GetMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String reviseForm(@PathVariable Long id, Model model) {
        model.addAttribute("packingListForm", toForm(packingListService.getById(id)));
        model.addAttribute("isEdit", true);
        model.addAttribute("isChange", true);
        return "packing-list/form";
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String revise(@PathVariable Long id,
                         @Validated({Default.class, ReviseGroup.class}) @ModelAttribute("packingListForm") PackingListForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareReviseModel(model);
            return "packing-list/form";
        }
        try {
            PackingListDto dto = packingListService.revise(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "装箱单变更成功");
            return "redirect:/crm/packing-lists/" + dto.id() + "/view";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareReviseModel(model);
            return "packing-list/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("packingListForm", toForm(packingListService.getById(id)));
        model.addAttribute("isEdit", true);
        return "packing-list/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("packingListForm") PackingListForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "packing-list/form";
        }
        try {
            packingListService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "packing-list/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "装箱单更新成功");
        return "redirect:/crm/packing-lists/" + id + "/view";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            packingListService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "装箱单删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/packing-lists";
    }

    private void prepareReviseModel(Model model) {
        model.addAttribute("isEdit", true);
        model.addAttribute("isChange", true);
    }

    private PackingListForm toForm(PackingListDto dto) {
        PackingListForm form = new PackingListForm();
        form.setId(dto.id());
        form.setPackingDate(dto.packingDate());
        form.setMarks(dto.marks());
        form.setNumberOfPackages(dto.numberOfPackages());
        form.setGrossWeight(dto.grossWeight());
        form.setNetWeight(dto.netWeight());
        form.setVolume(dto.volume());
        form.setRemark(dto.remark());
        return form;
    }
}
