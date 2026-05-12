package com.jugu.propertylease.main.iam.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.iam.repo.RoleRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamPermission;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class JooqRoleRepository implements RoleRepository {

    private final DSLContext dsl;

    public JooqRoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean existsByCode(String code) {
        return dsl.fetchExists(
                dsl.selectOne().from(IAM_ROLE).where(IAM_ROLE.CODE.eq(code))
        );
    }

    @Override
    public Long insert(String name, String code, RoleType roleType,
                       DataScopeDimension dataScopeDimension, String description,
                       OffsetDateTime now) {
        return dsl.insertInto(IAM_ROLE)
                .set(IAM_ROLE.NAME, name)
                .set(IAM_ROLE.CODE, code)
                .set(IAM_ROLE.ROLE_TYPE, roleType.getValue())
                .set(IAM_ROLE.SOURCE_TYPE, SourceType.CUSTOM.getValue())
                .set(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION,
                        dataScopeDimension == null ? null : dataScopeDimension.getValue())
                .set(IAM_ROLE.DESCRIPTION, description)
                .set(IAM_ROLE.CREATED_AT, now)
                .set(IAM_ROLE.UPDATED_AT, now)
                .returning(IAM_ROLE.ID)
                .fetchOne(IAM_ROLE.ID);
    }

    @Override
    public Optional<IamRole> findById(Long roleId) {
        return Optional.ofNullable(
                dsl.selectFrom(IAM_ROLE)
                        .where(IAM_ROLE.ID.eq(roleId))
                        .fetchOneInto(IamRole.class)
        );
    }

    @Override
    public List<IamRole> findByIds(List<Long> roleIds) {
        return dsl.selectFrom(IAM_ROLE)
                .where(IAM_ROLE.ID.in(roleIds))
                .fetchInto(IamRole.class);
    }

    @Override
    public void updateBasic(Long roleId, String name, String description, OffsetDateTime now) {
        dsl.update(IAM_ROLE)
                .set(IAM_ROLE.NAME, name)
                .set(IAM_ROLE.DESCRIPTION, description)
                .set(IAM_ROLE.UPDATED_AT, now)
                .where(IAM_ROLE.ID.eq(roleId))
                .execute();
    }

    @Override
    public List<IamPermission> findActivePermissionsByRoleId(Long roleId) {
        return dsl.select(IAM_PERMISSION.fields())
                .from(IAM_ROLE_PERMISSION)
                .join(IAM_PERMISSION).on(IAM_ROLE_PERMISSION.PERMISSION_ID.eq(IAM_PERMISSION.ID))
                .where(IAM_ROLE_PERMISSION.ROLE_ID.eq(roleId))
                .and(IAM_PERMISSION.DELETED_AT.isNull())
                .orderBy(IAM_PERMISSION.RESOURCE.asc(), IAM_PERMISSION.ACTION.asc(), IAM_PERMISSION.ID.asc())
                .fetchInto(IamPermission.class);
    }

    @Override
    public Set<Long> findActivePermissionIdsByIds(List<Long> permissionIds) {
        return new LinkedHashSet<>(
                dsl.select(IAM_PERMISSION.ID)
                        .from(IAM_PERMISSION)
                        .where(IAM_PERMISSION.ID.in(permissionIds))
                        .and(IAM_PERMISSION.DELETED_AT.isNull())
                        .fetch(IAM_PERMISSION.ID)
        );
    }

    /**
     * 全量替换角色权限，同时更新 updated_at。
     *
     * <p>原实现逐条 execute()，改为 jOOQ 多值批量 INSERT，减少往返次数。
     */
    @Override
    public void replacePermissions(Long roleId, List<Long> permissionIds, OffsetDateTime now) {
        dsl.deleteFrom(IAM_ROLE_PERMISSION)
                .where(IAM_ROLE_PERMISSION.ROLE_ID.eq(roleId))
                .execute();

        if (!permissionIds.isEmpty()) {
            var insert = dsl.insertInto(IAM_ROLE_PERMISSION,
                    IAM_ROLE_PERMISSION.ROLE_ID, IAM_ROLE_PERMISSION.PERMISSION_ID);
            for (Long permissionId : permissionIds) {
                insert = insert.values(roleId, permissionId);
            }
            insert.execute();
        }

        // 权限变更同步更新角色时间戳，无需调用方额外调用 touchUpdatedAt
        dsl.update(IAM_ROLE)
                .set(IAM_ROLE.UPDATED_AT, now)
                .where(IAM_ROLE.ID.eq(roleId))
                .execute();
    }

    @Override
    public boolean isAnyRoleAssignedToUser(List<Long> roleIds) {
        return dsl.fetchExists(
                dsl.selectOne().from(IAM_USER_ROLE).where(IAM_USER_ROLE.ROLE_ID.in(roleIds))
        );
    }

    @Override
    public void deletePermissionsByRoleIds(List<Long> roleIds) {
        dsl.deleteFrom(IAM_ROLE_PERMISSION)
                .where(IAM_ROLE_PERMISSION.ROLE_ID.in(roleIds))
                .execute();
    }

    @Override
    public void deleteByIds(List<Long> roleIds) {
        dsl.deleteFrom(IAM_ROLE)
                .where(IAM_ROLE.ID.in(roleIds))
                .execute();
    }
}
