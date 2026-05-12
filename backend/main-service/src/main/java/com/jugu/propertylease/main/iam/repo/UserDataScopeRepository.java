package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.api.model.DataScopeItem;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * iam_user_data_scope 表的数据操作契约。
 *
 * <p>数据权限采用全量替换策略：每次更新先清空再写入，
 * 因此对外只暴露一个 replace 方法，不拆分为 clear + insert。
 */
public interface UserDataScopeRepository {

    /**
     * 全量替换用户数据权限范围。
     *
     * <p>实现：先 DELETE WHERE user_id=?，再按 DataScopeItem 列表 INSERT。
     * <ul>
     *   <li>scopeType=ALL：插入一条 resource_id=NULL 的记录</li>
     *   <li>scopeType=SPECIFIC：按 resourceIds 插入多条记录</li>
     * </ul>
     *
     * @param userId 用户 ID
     * @param scopes 新的数据权限列表（空列表表示清空所有权限）
     * @param now    操作时间
     */
    void replace(Long userId, List<DataScopeItem> scopes, OffsetDateTime now);
}
