package com.jugu.propertylease.main.iam.page;

import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;

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
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.User;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.page.options.ScopeOption;
import com.jugu.propertylease.main.iam.page.options.UserScopeOptionProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.RecordMapper;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.TableLike;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public final class IamUsersPageResource implements JooqPageResourceDefinition<User> {

  private static final String DIMENSION_AREA = "AREA";
  private static final String DIMENSION_STORE = "STORE";
  private static final String SCOPE_ALL = "ALL";
  private static final String SCOPE_SPECIFIC = "SPECIFIC";
  private static final String ROLE_NAMES_ALIAS = "roleNames";

  private static final Field<String> ROLE_NAMES_FIELD = DSL.field(
          "(select coalesce(group_concat(distinct r.name order by r.name separator ','), '-') "
              + "from iam_user_role ur join iam_role r on ur.role_id = r.id where ur.user_id = {0})",
          String.class, IAM_USER.ID)
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
    Map<String, List<String>> allowedStoreValuesByArea = toAllowedMapping(
        scopeOptionProvider.allowedStoreIdsByAreaId());

    Map<String, FilterBinding> bindings = new LinkedHashMap<>();
    bindings.put("areaId", new IdsFilterBinding(
        "areaId",
        "区域名称",
        this::toAreaCondition,
        areaOptions,
        List.of(),
        true,
        Map.of()));
    bindings.put("storeId", new IdsFilterBinding(
        "storeId",
        "门店名称",
        this::toStoreCondition,
        storeOptions,
        List.of("areaId"),
        true,
        allowedStoreValuesByArea));
    bindings.put("username", StringFilterBinding.likeIgnoreCase("username", "登录名", IAM_USER.USER_NAME));
    bindings.put("userType", EnumFilterBinding.eqString("userType", "用户类型", IAM_USER.USER_TYPE,
        List.of(option("STAFF", "内部员工"), option("TENANT", "租户用户"),
            option("CONTRACTOR", "外部协作"),
            option("SYSTEM", "系统"))));
    bindings.put("mobile", StringFilterBinding.likeIgnoreCase("mobile", "手机号", IAM_USER.MOBILE));
    return Map.copyOf(bindings);
  }

  private List<FilterOption> toOptions(List<ScopeOption> source) {
    return source.stream()
        .map(it -> new FilterOption().value(String.valueOf(it.id())).label(it.label()))
        .toList();
  }

  private Map<String, List<String>> toAllowedMapping(Map<Long, List<Long>> source) {
    return source.entrySet().stream()
        .collect(Collectors.toMap(
            it -> String.valueOf(it.getKey()),
            it -> it.getValue().stream().map(String::valueOf).toList(),
            (a, b) -> a,
            LinkedHashMap::new));
  }

  private Condition toAreaCondition(List<Long> ids) {
    if (ids.size() != 1) {
      throw new IllegalArgumentException("areaId filter expects exactly one id");
    }
    return dataScopeCondition(DIMENSION_AREA, ids.get(0));
  }

  private Condition toStoreCondition(List<Long> ids) {
    if (ids.size() != 1) {
      throw new IllegalArgumentException("storeId filter expects exactly one id");
    }
    return dataScopeCondition(DIMENSION_STORE, ids.get(0));
  }

  private Condition dataScopeCondition(String dimension, Long resourceId) {
    return DSL.exists(DSL.selectOne()
        .from(IAM_USER_DATA_SCOPE)
        .where(IAM_USER_DATA_SCOPE.USER_ID.eq(IAM_USER.ID))
        .and(IAM_USER_DATA_SCOPE.SCOPE_DIMENSION.eq(dimension))
        .and(IAM_USER_DATA_SCOPE.SCOPE_TYPE.eq(SCOPE_ALL)
            .or(IAM_USER_DATA_SCOPE.SCOPE_TYPE.eq(SCOPE_SPECIFIC)
                .and(IAM_USER_DATA_SCOPE.RESOURCE_ID.eq(resourceId)))));
  }

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

  private ColumnMeta column(String key, String label, ColumnMeta.ValueTypeEnum type) {
    return new ColumnMeta().key(key).label(label).valueType(type).visible(true).sortable(false);
  }

  private FilterOption option(String value, String label) {
    return new FilterOption().value(value).label(label);
  }

  @Override
  public List<SelectFieldOrAsterisk> selectFields() {
    return List.of(IAM_USER.ID, IAM_USER.USER_NAME, IAM_USER.REAL_NAME, IAM_USER.MOBILE,
        IAM_USER.EMAIL,
        IAM_USER.USER_TYPE, IAM_USER.SOURCE_TYPE, IAM_USER.SOURCE, IAM_USER.CREATED_AT,
        IAM_USER.UPDATED_AT,
        IAM_USER.STATUS, ROLE_NAMES_FIELD);
  }

  @Override
  public TableLike<?> from() {
    return IAM_USER;
  }

  @Override
  public Condition baseCondition() {
    return IAM_USER.DELETED_AT.isNull();
  }

  @Override
  public List<SortField<?>> defaultSorts() {
    return List.of(IAM_USER.ID.desc());
  }

  @Override
  public RecordMapper<org.jooq.Record, User> rowMapper() {
    return record -> new User().id(record.get(IAM_USER.ID)).userName(record.get(IAM_USER.USER_NAME))
        .realName(record.get(IAM_USER.REAL_NAME)).mobile(record.get(IAM_USER.MOBILE))
        .email(record.get(IAM_USER.EMAIL))
        .userType(UserType.fromValue(record.get(IAM_USER.USER_TYPE)))
        .sourceType(SourceType.fromValue(record.get(IAM_USER.SOURCE_TYPE)))
        .source(record.get(IAM_USER.SOURCE))
        .createdAt(record.get(IAM_USER.CREATED_AT)).updatedAt(record.get(IAM_USER.UPDATED_AT))
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
}

