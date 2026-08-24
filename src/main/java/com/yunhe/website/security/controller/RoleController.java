package com.yunhe.website.security.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.security.dto.PermissionDto;
import com.yunhe.website.security.dto.RoleDto;
import com.yunhe.website.security.dto.request.RoleForm;
import com.yunhe.website.security.service.PermissionService;
import com.yunhe.website.security.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 角色管理控制器。
 */
@Controller
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).ROLE_LIST)")
    public String list(Model model) {
        model.addAttribute("roles", roleService.list());
        return "role/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).ROLE_CREATE)")
    public String createForm(Model model) {
        model.addAttribute("roleForm", new RoleForm());
        prepareFormModel(model, false);
        return "role/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).ROLE_CREATE)")
    public String create(@Valid @ModelAttribute("roleForm") RoleForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, false);
            return "role/form";
        }
        try {
            roleService.create(form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, false);
            return "role/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "角色创建成功");
        return "redirect:/admin/roles";
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).ROLE_LIST)")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("role", roleService.getById(id));
        return "role/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).ROLE_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("roleForm", toForm(roleService.getById(id)));
        prepareFormModel(model, true);
        return "role/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).ROLE_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("roleForm") RoleForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, true);
            return "role/form";
        }
        try {
            roleService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, true);
            return "role/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "角色更新成功");
        return "redirect:/admin/roles";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).ROLE_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            roleService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "角色删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/roles";
    }

    private void prepareFormModel(Model model, boolean isEdit) {
        Map<String, List<PermissionDto>> grouped = permissionService.listGroupedByModule();
        model.addAttribute("permissionGroups", grouped);
        model.addAttribute("isEdit", isEdit);
    }

    private RoleForm toForm(RoleDto dto) {
        RoleForm form = new RoleForm();
        form.setId(dto.id());
        form.setCode(dto.code());
        form.setName(dto.name());
        form.setNameZh(dto.nameZh());
        form.setDescription(dto.description());
        form.setEnabled(dto.enabled());
        form.setSortOrder(dto.sortOrder());
        form.setPermissionIds(dto.permissionIds() == null ? Set.of() : dto.permissionIds());
        return form;
    }
}
