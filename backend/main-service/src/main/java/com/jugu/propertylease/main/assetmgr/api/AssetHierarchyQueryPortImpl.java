package com.jugu.propertylease.main.assetmgr.api;

import static com.jugu.propertylease.main.jooq.Tables.AREA_INFO;
import static com.jugu.propertylease.main.jooq.Tables.STORE_INFO;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link AssetHierarchyQueryPort} 的 jOOQ 实现，IsDeleted=1 表示未删除。
 */
@Component
public class AssetHierarchyQueryPortImpl implements AssetHierarchyQueryPort {

    private final DSLContext dsl;

    public AssetHierarchyQueryPortImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Set<Long> findExistingAreaIds(Collection<Long> areaIds) {
        if (areaIds == null || areaIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(dsl.select(AREA_INFO.AREAID)
                .from(AREA_INFO)
                .where(AREA_INFO.AREAID.in(areaIds).and(AREA_INFO.ISDELETED.eq(1)))
                .fetch(AREA_INFO.AREAID));
    }

    @Override
    public Set<Long> findExistingStoreIds(Collection<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(dsl.select(STORE_INFO.STOREID)
                .from(STORE_INFO)
                .where(STORE_INFO.STOREID.in(storeIds).and(STORE_INFO.ISDELETED.eq(1)))
                .fetch(STORE_INFO.STOREID));
    }
}
