package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.repo.UserRoleRepository;
import com.jugu.propertylease.main.iam.repo.model.RoleTypeSnapshot;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class JooqUserRoleRepository implements UserRoleRepository {

    private final DSLContext dsl;

    public JooqUserRoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<RoleTypeSnapshot> findSnapshotsByIds(List<Long> roleIds) {
        return dsl.select(IAM_ROLE.ID, IAM_ROLE.ROLE_TYPE, IAM_ROLE.NAME)
                .from(IAM_ROLE)
                .where(IAM_ROLE.ID.in(roleIds))
                .fetch(r -> new RoleTypeSnapshot(
                        r.get(IAM_ROLE.ID),
                        // iam_role.role_type 存储的是 UserType 值（STAFF/CONTRACTOR 等）
                        // 表示"此角色归属哪种用户类型"，与 BUILTIN/CUSTOM 无关
                        UserType.fromValue(r.get(IAM_ROLE.ROLE_TYPE)),
                        r.get(IAM_ROLE.NAME)));
    }

    @Override
    public Set<String> findRequiredScopeDimensionsByIds(List<Long> roleIds) {
        return new LinkedHashSet<>(
                dsl.select(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION)
                        .from(IAM_ROLE)
                        .where(IAM_ROLE.ID.in(roleIds))
                        .and(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION.isNotNull())
                        .fetch(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION)
        );
    }

    @Override
    public List<Long> findRoleIdsByUserId(Long userId) {
        return dsl.select(IAM_USER_ROLE.ROLE_ID)
                .from(IAM_USER_ROLE)
                .where(IAM_USER_ROLE.USER_ID.eq(userId))
                .fetch(IAM_USER_ROLE.ROLE_ID);
    }

    @Override
    public void replace(Long userId, List<Long> roleIds, OffsetDateTime now) {
        dsl.deleteFrom(IAM_USER_ROLE)
                .where(IAM_USER_ROLE.USER_ID.eq(userId))
                .execute();

        // 批量 INSERT，比逐条 execute() 减少往返次数
        var insert = dsl.insertInto(IAM_USER_ROLE,
                IAM_USER_ROLE.USER_ID, IAM_USER_ROLE.ROLE_ID, IAM_USER_ROLE.CREATED_AT);
        for (Long roleId : roleIds) {
            insert = insert.values(userId, roleId, now);
        }
        insert.execute();
    }
}
