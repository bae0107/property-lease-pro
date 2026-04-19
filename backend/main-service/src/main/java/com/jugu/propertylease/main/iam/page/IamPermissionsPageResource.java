package com.jugu.propertylease.main.iam.page;

import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;

import com.jugu.propertylease.common.pagination.jooq.JooqPageResourceDefinition;
import com.jugu.propertylease.common.pagination.jooq.schema.JooqPageSchema;
import com.jugu.propertylease.common.pagination.jooq.schema.PageSchemaAutoGenerator;
import com.jugu.propertylease.common.pagination.jooq.schema.PageSchemaOverride;
import com.jugu.propertylease.main.api.model.Permission;
import java.util.List;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.RecordMapper;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.TableLike;
import org.springframework.stereotype.Component;


@Component
public final class IamPermissionsPageResource implements JooqPageResourceDefinition<Permission> {

  private final JooqPageSchema pageSchema;

  public IamPermissionsPageResource(PageSchemaAutoGenerator generator) {
    PageSchemaOverride override = PageSchemaOverride.builder().labelOverride("code", "权限码")
        .labelOverride("name",
            "显示名称").labelOverride("resource", "资源标识").labelOverride("action", "操作标识")
        .labelOverride("description", "描述").labelOverride("createdAt", "创建时间")
        .includeColumns(
            List.of("id", "code", "name", "resource", "action", "description", "createdAt"))
        .filterableColumns(Set.of("id", "code", "name", "resource", "action"))
        .columnOrder(
            List.of("id", "code", "name", "resource", "action", "description", "createdAt"))
        .build();
    this.pageSchema = generator.generate(IAM_PERMISSION, override);
  }

  public List<SelectFieldOrAsterisk> selectFields() {
    return List.of(IAM_PERMISSION.ID, IAM_PERMISSION.CODE, IAM_PERMISSION.NAME,
        IAM_PERMISSION.RESOURCE,
        IAM_PERMISSION.ACTION, IAM_PERMISSION.DESCRIPTION, IAM_PERMISSION.CREATED_AT,
        IAM_PERMISSION.UPDATED_AT);
  }

  public TableLike<?> from() {
    return IAM_PERMISSION;
  }

  public Condition baseCondition() {
    return IAM_PERMISSION.DELETED_AT.isNull();
  }

  public List<SortField<?>> defaultSorts() {
    return List.of(IAM_PERMISSION.CODE.asc());
  }

  public RecordMapper<org.jooq.Record, Permission> rowMapper() {
    return record -> new Permission().id(record.get(IAM_PERMISSION.ID))
        .code(record.get(IAM_PERMISSION.CODE))
        .name(record.get(IAM_PERMISSION.NAME)).resource(record.get(IAM_PERMISSION.RESOURCE))
        .action(record.get(IAM_PERMISSION.ACTION))
        .description(record.get(IAM_PERMISSION.DESCRIPTION))
        .createdAt(record.get(IAM_PERMISSION.CREATED_AT))
        .updatedAt(record.get(IAM_PERMISSION.UPDATED_AT));
  }

  public JooqPageSchema pageSchema() {
    return pageSchema;
  }
}
