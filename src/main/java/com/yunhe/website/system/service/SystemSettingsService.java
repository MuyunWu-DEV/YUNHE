package com.yunhe.website.system.service;

import com.yunhe.website.system.dto.request.SystemSettingsForm;
import com.yunhe.website.system.entity.SystemSettings;
import com.yunhe.website.system.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统设置服务（单例）。
 */
@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final SystemSettingsRepository settingsRepository;

    /** 获取系统设置（不存在则返回空对象，供页面回显） */
    @Transactional(readOnly = true)
    public SystemSettings get() {
        return settingsRepository.findFirstByOrderByIdAsc().orElseGet(SystemSettings::new);
    }

    /** 保存系统设置 */
    @Transactional
    public void update(SystemSettingsForm form) {
        SystemSettings settings = settingsRepository.findFirstByOrderByIdAsc().orElseGet(SystemSettings::new);
        settings.setSeller(form.getSeller());
        settings.setTerms(form.getTerms());
        settings.setIncoterms(form.getIncoterms());
        settings.setBankAccountInformation(form.getBankAccountInformation());
        settingsRepository.save(settings);
    }
}
