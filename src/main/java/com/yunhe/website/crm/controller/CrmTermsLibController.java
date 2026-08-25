package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.crm.dto.request.CrmTermsLibForm;
import com.yunhe.website.crm.entity.CrmTermsLib;
import com.yunhe.website.crm.service.CrmTermsLibService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 条款库控制器：维护 PI 单证默认的卖方 / 银行账户 / 条款 / 其他信息（支持多公司多条记录）。
 */
@Controller
@RequestMapping("/crm/terms-lib")
@RequiredArgsConstructor
public class CrmTermsLibController {

    private final CrmTermsLibService termsLibService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).TERMS_LIB_LIST)")
    public String list(Model model) {
        model.addAttribute("termsLibs", termsLibService.list());
        return "crm/terms-lib/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).TERMS_LIB_CREATE)")
    public String createForm(Model model) {
        model.addAttribute("termsLibForm", new CrmTermsLibForm());
        model.addAttribute("isEdit", false);
        return "crm/terms-lib/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).TERMS_LIB_CREATE)")
    public String create(@ModelAttribute("termsLibForm") CrmTermsLibForm form,
                         RedirectAttributes redirectAttributes) {
        termsLibService.create(form);
        redirectAttributes.addFlashAttribute("successMessage", "条款库创建成功");
        return "redirect:/crm/terms-lib";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).TERMS_LIB_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("termsLibForm", toForm(termsLibService.getById(id)));
        model.addAttribute("isEdit", true);
        return "crm/terms-lib/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).TERMS_LIB_UPDATE)")
    public String update(@PathVariable Long id,
                         @ModelAttribute("termsLibForm") CrmTermsLibForm form,
                         RedirectAttributes redirectAttributes) {
        termsLibService.update(id, form);
        redirectAttributes.addFlashAttribute("successMessage", "条款库更新成功");
        return "redirect:/crm/terms-lib";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).TERMS_LIB_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            termsLibService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "条款库删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/terms-lib";
    }

    private CrmTermsLibForm toForm(CrmTermsLib lib) {
        CrmTermsLibForm form = new CrmTermsLibForm();
        form.setId(lib.getId());
        form.setCompanyNameChinese(lib.getCompanyNameChinese());
        form.setCompanyNameEnglish(lib.getCompanyNameEnglish());
        form.setAddress(lib.getAddress());
        form.setPhone(lib.getPhone());
        form.setEmail(lib.getEmail());
        form.setBeneficiaryName(lib.getBeneficiaryName());
        form.setBeneficiaryAddress(lib.getBeneficiaryAddress());
        form.setBankName(lib.getBankName());
        form.setBankAddress(lib.getBankAddress());
        form.setAccountNo(lib.getAccountNo());
        form.setSwiftCode(lib.getSwiftCode());
        form.setCountryOfOrigin(lib.getCountryOfOrigin());
        form.setPortOfDelivery(lib.getPortOfDelivery());
        form.setTimeOfDelivery(lib.getTimeOfDelivery());
        form.setPaymentTerm(lib.getPaymentTerm());
        form.setPacking(lib.getPacking());
        form.setNote(lib.getNote());
        form.setIncoterms(lib.getIncoterms());
        form.setWarranty(lib.getWarranty());
        return form;
    }
}
