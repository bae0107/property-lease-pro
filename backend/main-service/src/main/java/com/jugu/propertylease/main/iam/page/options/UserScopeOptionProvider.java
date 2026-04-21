package com.jugu.propertylease.main.iam.page.options;

import java.util.List;
import java.util.Map;

/**
 * 用户分页筛选项（区域/门店）数据提供接口。
 *
 * <p>当前由 main-service 内部适配器提供数据，后续可替换为调用其他模块 API 的实现。
 */
public interface UserScopeOptionProvider {

  /**
   * 返回可选区域列表。
   */
  List<ScopeOption> listAreas();

  /**
   * 返回可选门店列表。
   */
  List<ScopeOption> listStores();

  /**
   * 返回“区域 -> 可选门店ID列表”的依赖映射。
   */
  Map<Long, List<Long>> allowedStoreIdsByAreaId();
}

