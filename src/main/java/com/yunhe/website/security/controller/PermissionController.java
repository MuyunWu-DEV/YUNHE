package com.yunhe.website.security.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.security.dto.PermissionDto;
import com.yunhe.website.security.dto.request.PermissionForm;
import com.yunhe.website.security.service.PermissionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 权限管理控制器。
 */
@Controller
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PERMISSION_LIST)")
    public String list(Model model) {
        Map<String, List<PermissionDto>> grouped = permissionService.listGroupedByModule();
        model.addAttribute("permissionGroups", grouped);
        return "permission/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PERMISSION_CREATE)")
    public String createForm(Model model) {
        model.addAttribute("permissionForm", new PermissionForm());
        model.addAttribute("isEdit", false);
        return "permission/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PERMISSION_CREATE)")
    public String create(@Valid @ModelAttribute("permissionForm") PermissionForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "permission/form";
        }
        try {
            permissionService.create(form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            return "permission/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "权限创建成功");
        return "redirect:/admin/permissions";
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PERMISSION_LIST)")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("permission", permissionService.getById(id));
        return "permission/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PERMISSION_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("permissionForm", toForm(permissionService.getById(id)));
        model.addAttribute("isEdit", true);
        return "permission/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PERMISSION_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("permissionForm") PermissionForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "permission/form";
        }
        try {
            permissionService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "permission/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "权限更新成功");
        return "redirect:/admin/permissions";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PERMISSION_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            permissionService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "权限删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/permissions";
    }

    private PermissionForm toForm(PermissionDto dto) {
        PermissionForm form = new PermissionForm();
        form.setId(dto.id());
        form.setCode(dto.code());
        form.setName(dto.name());
        form.setNameZh(dto.nameZh());
        form.setModule(dto.module());
        form.setResource(dto.resource());
        form.setAction(dto.action());
        form.setDescription(dto.description());
        form.setSortOrder(dto.sortOrder());
        return form;
    }
}
