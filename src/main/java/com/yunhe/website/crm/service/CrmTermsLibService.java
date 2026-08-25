package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.crm.dto.request.CrmTermsLibForm;
import com.yunhe.website.crm.entity.CrmTermsLib;
import com.yunhe.website.crm.repository.CrmTermsLibRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 条款库服务：维护 PI 单证默认的卖方 / 银行账户 / 条款 / 其他信息（支持多公司多条记录）。
 */
@Service
@RequiredArgsConstructor
public class CrmTermsLibService {

    private final CrmTermsLibRepository termsLibRepository;

    /** 全部条款库记录（列表页） */
    @Transactional(readOnly = true)
    public List<CrmTermsLib> list() {
        return termsLibRepository.findAllByOrderByIdAsc();
    }

    /** 默认条款库（第一条，供 PI 一键导入；不存在返回空对象） */
    @Transactional(readOnly = true)
    public CrmTermsLib get() {
        return termsLibRepository.findFirstByOrderByIdAsc().orElseGet(CrmTermsLib::new);
    }

    /** 单条记录 */
    @Transactional(readOnly = true)
    public CrmTermsLib getById(Long id) {
        return termsLibRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("条款库", id));
    }

    /** 新增 */
    @Transactional
    public void create(CrmTermsLibForm form) {
        CrmTermsLib lib = new CrmTermsLib();
        applyForm(lib, form);
        termsLibRepository.save(lib);
    }

    /** 更新 */
    @Transactional
    public void update(Long id, CrmTermsLibForm form) {
        CrmTermsLib lib = getById(id);
        applyForm(lib, form);
        termsLibRepository.save(lib);
    }

    /** 删除 */
    @Transactional
    public void delete(Long id) {
        CrmTermsLib lib = getById(id);
        termsLibRepository.delete(lib);
    }

    private void applyForm(CrmTermsLib lib, CrmTermsLibForm form) {
        lib.setCompanyNameChinese(form.getCompanyNameChinese());
        lib.setCompanyNameEnglish(form.getCompanyNameEnglish());
        lib.setAddress(form.getAddress());
        lib.setPhone(form.getPhone());
        lib.setEmail(form.getEmail());
        lib.setBeneficiaryName(form.getBeneficiaryName());
        lib.setBeneficiaryAddress(form.getBeneficiaryAddress());
        lib.setBankName(form.getBankName());
        lib.setBankAddress(form.getBankAddress());
        lib.setAccountNo(form.getAccountNo());
        lib.setSwiftCode(form.getSwiftCode());
        lib.setCountryOfOrigin(form.getCountryOfOrigin());
        lib.setPortOfDelivery(form.getPortOfDelivery());
        lib.setTimeOfDelivery(form.getTimeOfDelivery());
        lib.setPaymentTerm(form.getPaymentTerm());
        lib.setPacking(form.getPacking());
        lib.setNote(form.getNote());
        lib.setIncoterms(form.getIncoterms());
        lib.setWarranty(form.getWarranty());
    }

    // ===== 便捷读取（供 PI 预填，取默认第一条） =====

    @Transactional(readOnly = true)
    public String getCompanyNameEnglish() {
        return get().getCompanyNameEnglish();
    }

    @Transactional(readOnly = true)
    public String getAddress() {
        return get().getAddress();
    }

    @Transactional(readOnly = true)
    public String getPhone() {
        return get().getPhone();
    }

    @Transactional(readOnly = true)
    public String getEmail() {
        return get().getEmail();
    }

    @Transactional(readOnly = true)
    public String getIncoterms() {
        return get().getIncoterms();
    }

    @Transactional(readOnly = true)
    public String getWarranty() {
        return get().getWarranty();
    }

    /** 条款（6 字段 → 多行文本，供 PI textarea，默认第一条） */
    @Transactional(readOnly = true)
    public String getTerms() {
        return termsText(get());
    }

    /** 银行账户信息（6 字段 → 多行文本，供 PI textarea，默认第一条） */
    @Transactional(readOnly = true)
    public String getBankAccountInformation() {
        return bankText(get());
    }

    /** PI 表单预填视图（默认第一条） */
    @Transactional(readOnly = true)
    public Map<String, String> getPrefillMap() {
        return prefillMapOf(get());
    }

    /** 按条款库 ID 取预填视图（id 为空时取第一条） */
    @Transactional(readOnly = true)
    public Map<String, String> getPrefillMap(Long id) {
        return prefillMapOf(id == null ? get() : getById(id));
    }

    /** 从指定条款库生成预填视图（seller 拆 4 字段 + terms/bank 多行文本） */
    public Map<String, String> prefillMapOf(CrmTermsLib lib) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("sellerCompanyName", lib.getCompanyNameEnglish());
        map.put("sellerAddress", lib.getAddress());
        map.put("sellerPhone", lib.getPhone());
        map.put("sellerEmail", lib.getEmail());
        map.put("incoterms", lib.getIncoterms());
        map.put("terms", termsText(lib));
        map.put("bankAccountInformation", bankText(lib));
        map.put("warranty", lib.getWarranty());
        return map;
    }

    /** 条款文本（基于指定记录） */
    private static String termsText(CrmTermsLib lib) {
        return join(
                line("Country of Origin", lib.getCountryOfOrigin()),
                line("Port of Delivery", lib.getPortOfDelivery()),
                line("Time of Delivery", lib.getTimeOfDelivery()),
                line("Payment Term", lib.getPaymentTerm()),
                line("Packing", lib.getPacking()),
                line("Note", lib.getNote()));
    }

    /** 银行账户文本（基于指定记录） */
    private static String bankText(CrmTermsLib lib) {
        return join(
                line("Beneficiary Name", lib.getBeneficiaryName()),
                line("Beneficiary Address", lib.getBeneficiaryAddress()),
                line("Bank Name", lib.getBankName()),
                line("Bank Address", lib.getBankAddress()),
                line("Account No.", lib.getAccountNo()),
                line("SWIFT Code", lib.getSwiftCode()));
    }

    // ===== helper =====

    private static String line(String label, String value) {
        return (value == null || value.isBlank()) ? null : label + ": " + value;
    }

    private static String join(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line != null) {
                sb.append(line).append('\n');
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
