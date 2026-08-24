package com.yunhe.website.system.controller;

import com.yunhe.website.system.dto.request.SystemSettingsForm;
import com.yunhe.website.system.entity.SystemSettings;
import com.yunhe.website.system.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 系统设置控制器（维护形式发票默认的 Seller / Terms / Bank Account Information）。
 */
@Controller
@RequestMapping("/system/settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    @GetMapping("/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SYSTEM_SETTINGS_UPDATE)")
    public String editForm(Model model) {
        model.addAttribute("settingsForm", toForm(systemSettingsService.get()));
        return "system/settings";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).SYSTEM_SETTINGS_UPDATE)")
    public String update(@ModelAttribute("settingsForm") SystemSettingsForm form,
                         RedirectAttributes redirectAttributes) {
        systemSettingsService.update(form);
        redirectAttributes.addFlashAttribute("successMessage", "系统设置保存成功");
        return "redirect:/system/settings/edit";
    }

    private SystemSettingsForm toForm(SystemSettings settings) {
        SystemSettingsForm form = new SystemSettingsForm();
        form.setId(settings.getId());
        form.setSeller(settings.getSeller());
        form.setTerms(settings.getTerms());
        form.setIncoterms(settings.getIncoterms());
        form.setBankAccountInformation(settings.getBankAccountInformation());
        return form;
    }
}
