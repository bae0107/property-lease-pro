package com.jugu.propertylease.main.accounting.repo;

import java.util.Optional;

/**
 * 全局配置仓储（system_config）。
 */
public interface SystemConfigRepository {
    Optional<String> getValue(String key);
}
