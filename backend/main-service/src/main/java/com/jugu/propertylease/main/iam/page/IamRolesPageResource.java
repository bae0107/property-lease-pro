package com.jugu.propertylease.main.iam.page;

import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;

import com.jugu.propertylease.common.model.FilterOption;
import com.jugu.propertylease.common.pagination.jooq.JooqPageResourceDefinition;
import com.jugu.propertylease.common.pagination.jooq.schema.JooqPageSchema;
import com.jugu.propertylease.common.pagination.jooq.schema.PageSchemaAutoGenerator;
import com.jugu.propertylease.common.pagination.jooq.schema.PageSchemaOverride;
import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import java.util.List;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.RecordMapper;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.TableLike;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;


@Component
public final class IamRolesPageResource implements JooqPageResourceDefinition<Role> {

  private final JooqPageSchema pageSchema;

  public IamRolesPageResource(PageSchemaAutoGenerator generator) {
    PageSchemaOverride override = PageSchemaOverride.builder().labelOverride("name", "角色名称")
        .labelOverride("code", "角色编码").labelOverride("roleType", "角色域类型")
        .labelOverride("sourceType", "对象来源")
        .labelOverride("requiredDataScopeDimension", "数据权限绑定维度")
        .labelOverride("createdAt", "创建时间").includeColumns(
            List.of("id", "name", "code", "roleType", "sourceType", "requiredDataScopeDimension",
                "createdAt"))
        .filterableColumns(
            Set.of("id", "name", "code", "roleType", "sourceType", "requiredDataScopeDimension"))
        .enumOverride("roleType",
            List.of(option("STAFF", "内部员工"), option("TENANT", "租户用户"),
                option("CONTRACTOR", "外部协作"),
                option("SYSTEM", "系统")))
        .enumOverride("sourceType", List.of(option("BUILTIN", "内置"), option("CUSTOM", "自定义")))
        .enumOverride("requiredDataScopeDimension",
            List.of(option("AREA", "区域"), option("STORE", "门店")))
        .columnOrder(
            List.of("id", "name", "code", "roleType", "sourceType", "requiredDataScopeDimension",
                "createdAt"))
        .build();
    this.pageSchema = generator.generate(IAM_ROLE, override);
  }

  private FilterOption option(String value, String label) {
    return new FilterOption().value(value).label(label);
  }

  public List<SelectFieldOrAsterisk> selectFields() {
    return List.of(IAM_ROLE.ID, IAM_ROLE.NAME, IAM_ROLE.CODE, IAM_ROLE.ROLE_TYPE,
        IAM_ROLE.SOURCE_TYPE,
        IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION, IAM_ROLE.DESCRIPTION, IAM_ROLE.CREATED_AT,
        IAM_ROLE.UPDATED_AT);
  }

  public TableLike<?> from() {
    return IAM_ROLE;
  }

  public Condition baseCondition() {
    return DSL.trueCondition();
  }

  public List<SortField<?>> defaultSorts() {
    return List.of(IAM_ROLE.ID.desc());
  }

  public RecordMapper<org.jooq.Record, Role> rowMapper() {
    return record -> new Role().id(record.get(IAM_ROLE.ID)).name(record.get(IAM_ROLE.NAME))
        .code(record.get(IAM_ROLE.CODE))
        .roleType(RoleType.fromValue(record.get(IAM_ROLE.ROLE_TYPE)))
        .sourceType(SourceType.fromValue(record.get(IAM_ROLE.SOURCE_TYPE)))
        .requiredDataScopeDimension(
            DataScopeDimension.fromValue(record.get(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION)))
        .description(record.get(IAM_ROLE.DESCRIPTION)).createdAt(record.get(IAM_ROLE.CREATED_AT))
        .updatedAt(record.get(IAM_ROLE.UPDATED_AT));
  }

  public JooqPageSchema pageSchema() {
    return pageSchema;
  }
}
