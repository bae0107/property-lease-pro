package com.jugu.propertylease.main.iam.page.options;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 默认空实现。
 *
 * <p>后续接入其他模块 API 时，只需替换该 Bean。
 */
@Component
public class NoopUserScopeOptionProvider implements UserScopeOptionProvider {

  @Override
  public List<ScopeOption> listAreas() {
    return List.of();
  }

  @Override
  public List<ScopeOption> listStores() {
    return List.of();
  }

  @Override
  public Map<Long, List<Long>> allowedStoreIdsByAreaId() {
    return Map.of();
  }
}

