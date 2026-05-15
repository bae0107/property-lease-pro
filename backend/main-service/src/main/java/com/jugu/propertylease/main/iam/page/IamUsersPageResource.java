package com.jugu.propertylease.main.iam.page;

import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.common.model.ColumnMeta;
import com.jugu.propertylease.common.model.FilterFieldMeta;
import com.jugu.propertylease.common.model.FilterOption;
import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.pagination.jooq.JooqPageResourceDefinition;
import com.jugu.propertylease.common.pagination.jooq.binding.EnumFilterBinding;
import com.jugu.propertylease.common.pagination.jooq.binding.FilterBinding;
import com.jugu.propertylease.common.pagination.jooq.binding.IdsFilterBinding;
import com.jugu.propertylease.common.pagination.jooq.binding.StringFilterBinding;
import com.jugu.propertylease.common.pagination.jooq.schema.JooqPageSchema;
import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.DataScopeType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.User;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.page.options.ScopeOption;
import com.jugu.propertylease.main.iam.page.options.UserScopeOptionProvider;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.RecordMapper;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.TableLike;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public final class IamUsersPageResource implements JooqPageResourceDefinition<User> {

    private static final String ROLE_NAMES_ALIAS = "roleNames";

    // 与 IAM_USER 区分：JOIN 表使用语义化别名，阅读 SQL 链路更直观。
    private static final com.jugu.propertylease.main.jooq.tables.IamUserRole USER_ROLE = IAM_USER_ROLE.as("userRole");
    private static final com.jugu.propertylease.main.jooq.tables.IamRole ROLE = IAM_ROLE.as("role");

    private static final Field<String> ROLE_NAMES_FIELD = DSL.coalesce(
            DSL.groupConcatDistinct(ROLE.NAME).orderBy(ROLE.NAME.asc()).separator(","),
            DSL.inline("-"))
            .as(ROLE_NAMES_ALIAS);

    private final JooqPageSchema pageSchema;
    private final Map<String, FilterBinding> filterBindings;

    public IamUsersPageResource(UserScopeOptionProvider scopeOptionProvider) {
        this.filterBindings = buildFilterBindings(scopeOptionProvider);
        this.pageSchema = new JooqPageSchema(buildListViewMeta(this.filterBindings), this.filterBindings);
    }

    private Map<String, FilterBinding> buildFilterBindings(UserScopeOptionProvider scopeOptionProvider) {
        List<FilterOption> areaOptions = toOptions(scopeOptionProvider.listAreas());
        List<FilterOption> storeOptions = toOptions(scopeOptionProvider.listStores());
        Map<String, List<String>> allowedStoreValuesByArea =
                toAllowedMapping(scopeOptionProvider.allowedStoreIdsByAreaId());

        Map<String, FilterBinding> bindings = new LinkedHashMap<>();
        bindings.put("areaId", new IdsFilterBinding(
                "areaId", "区域名称",
                this::toAreaCondition,
                areaOptions, List.of(), true, Map.of()));
        bindings.put("storeId", new IdsFilterBinding(
                "storeId", "门店名称",
                this::toStoreCondition,
                storeOptions, List.of("areaId"), true, allowedStoreValuesByArea));
        bindings.put("username", StringFilterBinding.likeIgnoreCase(
                "username", "登录名", IAM_USER.USER_NAME));
        bindings.put("userType", EnumFilterBinding.eqString(
                "userType", "用户类型", IAM_USER.USER_TYPE,
                List.of(
                        option(UserType.STAFF.getValue(), "内部员工"),
                        option(UserType.TENANT.getValue(), "租户用户"),
                        option(UserType.CONTRACTOR.getValue(), "外部协作"),
                        option(UserType.SYSTEM.getValue(), "系统"))));
        bindings.put("mobile", StringFilterBinding.likeIgnoreCase(
                "mobile", "手机号", IAM_USER.MOBILE));
        return Map.copyOf(bindings);
    }

    private Condition toAreaCondition(List<Long> ids) {
        if (ids.size() != 1) {
            throw new IllegalArgumentException("areaId filter expects exactly one id");
        }
        return dataScopeCondition(DataScopeDimension.AREA, ids.get(0));
    }

    private Condition toStoreCondition(List<Long> ids) {
        if (ids.size() != 1) {
            throw new IllegalArgumentException("storeId filter expects exactly one id");
        }
        return dataScopeCondition(DataScopeDimension.STORE, ids.get(0));
    }

    /**
     * 生成数据权限过滤条件。
     *
     * <p>原实现使用 "ALL"/"SPECIFIC"/"AREA"/"STORE" 字符串字面量，
     * 现改用枚举 {@code .getValue()}，与 DB 存储值保持一致且类型安全。
     */
    private Condition dataScopeCondition(DataScopeDimension dimension, Long resourceId) {
        return DSL.exists(DSL.selectOne()
                .from(IAM_USER_DATA_SCOPE)
                .where(IAM_USER_DATA_SCOPE.USER_ID.eq(IAM_USER.ID))
                .and(IAM_USER_DATA_SCOPE.SCOPE_DIMENSION.eq(dimension.getValue()))
                .and(
                        IAM_USER_DATA_SCOPE.SCOPE_TYPE.eq(DataScopeType.ALL.getValue())
                                .or(IAM_USER_DATA_SCOPE.SCOPE_TYPE.eq(DataScopeType.SPECIFIC.getValue())
                                        .and(IAM_USER_DATA_SCOPE.RESOURCE_ID.eq(resourceId)))
                ));
    }

    // ─────────────────────────────────────────────
    // ListViewMeta 构建
    // ─────────────────────────────────────────────

    private ListViewMeta buildListViewMeta(Map<String, FilterBinding> bindings) {
        List<FilterFieldMeta> filters = List.of(
                bindings.get("areaId").toMeta(),
                bindings.get("storeId").toMeta(),
                bindings.get("username").toMeta(),
                bindings.get("userType").toMeta(),
                bindings.get("mobile").toMeta());

        List<ColumnMeta> columns = List.of(
                column("realName", "真实姓名", ColumnMeta.ValueTypeEnum.STRING),
                column("userName", "登录名", ColumnMeta.ValueTypeEnum.STRING),
                column("userType", "用户类型", ColumnMeta.ValueTypeEnum.ENUM),
                column("roleNames", "所属角色", ColumnMeta.ValueTypeEnum.STRING),
                column("mobile", "手机号", ColumnMeta.ValueTypeEnum.STRING),
                column("email", "邮箱号", ColumnMeta.ValueTypeEnum.STRING),
                column("status", "用户状态", ColumnMeta.ValueTypeEnum.ENUM));

        return new ListViewMeta().filters(filters).columns(columns);
    }

    // ─────────────────────────────────────────────
    // JooqPageResourceDefinition 实现
    // ─────────────────────────────────────────────

    @Override
    public List<SelectFieldOrAsterisk> selectFields() {
        return List.of(
                IAM_USER.ID, IAM_USER.USER_NAME, IAM_USER.REAL_NAME,
                IAM_USER.MOBILE, IAM_USER.EMAIL,
                IAM_USER.USER_TYPE, IAM_USER.SOURCE_TYPE, IAM_USER.SOURCE,
                IAM_USER.CREATED_AT, IAM_USER.UPDATED_AT,
                IAM_USER.STATUS, ROLE_NAMES_FIELD);
    }

    @Override
    public TableLike<?> from() {
        return IAM_USER
                .leftJoin(USER_ROLE).on(USER_ROLE.USER_ID.eq(IAM_USER.ID))
                .leftJoin(ROLE).on(USER_ROLE.ROLE_ID.eq(ROLE.ID));
    }

    @Override
    public Condition baseCondition() {
        return IAM_USER.DELETED_AT.isNull();
    }

    /**
     * selectFields() 定义“要返回哪些列”；
     * groupByFields() 定义“在存在聚合列（如 roleNames）时，哪些非聚合列用于分组去重”。
     */
    @Override
    public List<Field<?>> groupByFields() {
        return List.of(
                IAM_USER.ID, IAM_USER.USER_NAME, IAM_USER.REAL_NAME,
                IAM_USER.MOBILE, IAM_USER.EMAIL,
                IAM_USER.USER_TYPE, IAM_USER.SOURCE_TYPE, IAM_USER.SOURCE,
                IAM_USER.CREATED_AT, IAM_USER.UPDATED_AT,
                IAM_USER.STATUS);
    }

    @Override
    public List<SortField<?>> defaultSorts() {
        return List.of(IAM_USER.ID.desc());
    }

    @Override
    public RecordMapper<org.jooq.Record, User> rowMapper() {
        return record -> new User()
                .id(record.get(IAM_USER.ID))
                .userName(record.get(IAM_USER.USER_NAME))
                .realName(record.get(IAM_USER.REAL_NAME))
                .mobile(record.get(IAM_USER.MOBILE))
                .email(record.get(IAM_USER.EMAIL))
                .userType(UserType.fromValue(record.get(IAM_USER.USER_TYPE)))
                .sourceType(SourceType.fromValue(record.get(IAM_USER.SOURCE_TYPE)))
                .source(record.get(IAM_USER.SOURCE))
                .createdAt(record.get(IAM_USER.CREATED_AT))
                .updatedAt(record.get(IAM_USER.UPDATED_AT))
                .status(UserStatus.fromValue(record.get(IAM_USER.STATUS)))
                .roleNames(record.get(ROLE_NAMES_ALIAS, String.class));
    }

    @Override
    public JooqPageSchema pageSchema() {
        return pageSchema;
    }

    @Override
    public Map<String, FilterBinding> filterBindings() {
        return filterBindings;
    }

    // ─────────────────────────────────────────────
    // 私有辅助方法
    // ─────────────────────────────────────────────

    private List<FilterOption> toOptions(List<ScopeOption> source) {
        return source.stream()
                .map(it -> new FilterOption().value(String.valueOf(it.id())).label(it.label()))
                .toList();
    }

    private Map<String, List<String>> toAllowedMapping(Map<Long, List<Long>> source) {
        return source.entrySet().stream().collect(Collectors.toMap(
                it -> String.valueOf(it.getKey()),
                it -> it.getValue().stream().map(String::valueOf).toList(),
                (a, b) -> a,
                LinkedHashMap::new));
    }

    private ColumnMeta column(String key, String label, ColumnMeta.ValueTypeEnum type) {
        return new ColumnMeta().key(key).label(label).valueType(type).visible(true).sortable(false);
    }

    private FilterOption option(String value, String label) {
        return new FilterOption().value(value).label(label);
    }
}
