package com.yunhe.website.security.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.security.dto.UserDto;
import com.yunhe.website.security.dto.request.UserForm;
import com.yunhe.website.security.auth.CustomUserDetails;
import com.yunhe.website.security.service.RoleService;
import com.yunhe.website.security.service.UserService;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 用户管理控制器。
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_LIST)")
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<UserDto> users = userService.list(keyword, PageUtil.of(page, size));
        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, users.getTotalPages()));
        return "user/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_CREATE)")
    public String createForm(Model model) {
        model.addAttribute("userForm", new UserForm());
        model.addAttribute("roles", roleService.list());
        model.addAttribute("isEdit", false);
        return "user/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_CREATE)")
    public String create(@Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, false);
            return "user/form";
        }
        try {
            userService.create(form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, false);
            return "user/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "用户创建成功");
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_LIST)")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.getById(id));
        return "user/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("userForm", toForm(userService.getById(id)));
        model.addAttribute("roles", roleService.list());
        model.addAttribute("isEdit", true);
        return "user/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, true);
            return "user/form";
        }
        try {
            userService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareFormModel(model, true);
            return "user/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "用户更新成功");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).USER_DELETE)")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.delete(id, currentUser.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "用户删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    private void prepareFormModel(Model model, boolean isEdit) {
        model.addAttribute("roles", roleService.list());
        model.addAttribute("isEdit", isEdit);
    }

    private UserForm toForm(UserDto dto) {
        UserForm form = new UserForm();
        form.setId(dto.id());
        form.setUsername(dto.username());
        form.setFullName(dto.fullName());
        form.setEmail(dto.email());
        form.setPhone(dto.phone());
        form.setEnabled(dto.enabled());
        form.setAccountLocked(dto.accountLocked());
        Set<Long> roleIds = dto.roles() == null ? Set.of()
                : dto.roles().stream().map(UserDto.RoleSummary::id).collect(Collectors.toSet());
        form.setRoleIds(roleIds);
        return form;
    }
}
