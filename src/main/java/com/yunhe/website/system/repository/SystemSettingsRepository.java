package com.yunhe.website.system.repository;

import com.yunhe.website.system.entity.SystemSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系统设置仓储（单例）。
 */
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {

    Optional<SystemSettings> findFirstByOrderByIdAsc();
}
