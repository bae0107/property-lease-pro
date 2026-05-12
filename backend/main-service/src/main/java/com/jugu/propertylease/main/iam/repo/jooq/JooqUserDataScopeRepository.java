package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;

import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.DataScopeType;
import com.jugu.propertylease.main.iam.repo.UserDataScopeRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class JooqUserDataScopeRepository implements UserDataScopeRepository {

    private final DSLContext dsl;

    public JooqUserDataScopeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * 全量替换：先清除该用户所有数据权限，再按 scopes 重建。
     *
     * <p>replace 语义对外屏蔽了 clear + insert 的两步细节，
     * Service 层只需表达"我想要的最终状态"，不关心中间过程。
     */
    @Override
    public void replace(Long userId, List<DataScopeItem> scopes, OffsetDateTime now) {
        dsl.deleteFrom(IAM_USER_DATA_SCOPE)
                .where(IAM_USER_DATA_SCOPE.USER_ID.eq(userId))
                .execute();

        if (scopes == null || scopes.isEmpty()) {
            return;
        }

        var insert = dsl.insertInto(IAM_USER_DATA_SCOPE,
                IAM_USER_DATA_SCOPE.USER_ID,
                IAM_USER_DATA_SCOPE.SCOPE_DIMENSION,
                IAM_USER_DATA_SCOPE.SCOPE_TYPE,
                IAM_USER_DATA_SCOPE.RESOURCE_ID,
                IAM_USER_DATA_SCOPE.CREATED_AT);

        for (DataScopeItem item : scopes) {
            String dimension = item.getDimension().getValue();
            if (item.getScopeType() == DataScopeType.ALL) {
                insert = insert.values(userId, dimension, DataScopeType.ALL.getValue(), null, now);
            } else {
                // SPECIFIC：每个 resourceId 一行
                for (Long resourceId : item.getResourceIds()) {
                    insert = insert.values(userId, dimension, DataScopeType.SPECIFIC.getValue(), resourceId, now);
                }
            }
        }

        insert.execute();
    }
}
