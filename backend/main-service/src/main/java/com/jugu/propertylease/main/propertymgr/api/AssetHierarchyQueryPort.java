package com.jugu.propertylease.main.propertymgr.api;

import java.util.Collection;
import java.util.Set;

/**
 * 资产层级（区域/门店）查询 Port（进程内，供 iam 数据权限校验调用）。
 */
public interface AssetHierarchyQueryPort {

    /**
     * 在给定 ID 集合中，返回实际存在（未删除）的区域 ID 子集。
     */
    Set<Long> findExistingAreaIds(Collection<Long> areaIds);

    /**
     * 在给定 ID 集合中，返回实际存在（未删除）的门店 ID 子集。
     */
    Set<Long> findExistingStoreIds(Collection<Long> storeIds);
}
