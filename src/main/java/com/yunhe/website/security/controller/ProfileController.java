package com.yunhe.website.security.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.security.dto.request.ChangePasswordForm;
import com.yunhe.website.security.auth.CustomUserDetails;
import com.yunhe.website.security.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 个人中心控制器：修改密码。
 */
@Controller
@RequestMapping("/admin/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/password")
    public String changePasswordForm(Model model) {
        model.addAttribute("form", new ChangePasswordForm());
        return "profile/password";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute("form") ChangePasswordForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "profile/password";
        }
        try {
            userService.changePassword(userDetails.getUsername(), form);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/profile/password";
        }
        redirectAttributes.addFlashAttribute("successMessage", "密码修改成功");
        return "redirect:/admin/profile/password";
    }
}
