package com.jugu.propertylease.main.iam.page;

import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;

import com.jugu.propertylease.common.model.FilterOption;
import com.jugu.propertylease.common.pagination.jooq.JooqPageResourceDefinition;
import com.jugu.propertylease.common.pagination.jooq.schema.JooqPageSchema;
import com.jugu.propertylease.common.pagination.jooq.schema.PageSchemaAutoGenerator;
import com.jugu.propertylease.common.pagination.jooq.schema.PageSchemaOverride;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.User;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
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
public final class IamUsersPageResource implements JooqPageResourceDefinition<User> {

  private final JooqPageSchema pageSchema;

  public IamUsersPageResource(PageSchemaAutoGenerator generator) {
    PageSchemaOverride override = PageSchemaOverride.builder().keyOverride("userName", "username")
        .labelOverride("username", "用户名").labelOverride("realName", "真实姓名")
        .labelOverride("mobile", "手机号")
        .labelOverride("email", "邮箱").labelOverride("userType", "用户类型")
        .labelOverride("sourceType", "对象来源")
        .labelOverride("source", "创建渠道").labelOverride("status", "账号状态")
        .labelOverride("createdAt", "创建时间")
        .includeColumns(
            List.of("id", "username", "realName", "mobile", "email", "userType", "sourceType",
                "source", "status",
                "createdAt")).filterableColumns(
            Set.of("id", "username", "realName", "mobile", "email", "userType", "sourceType",
                "source", "status"))
        .enumOverride("userType",
            List.of(option("STAFF", "内部员工"), option("TENANT", "租户用户"),
                option("CONTRACTOR", "外部协作"),
                option("SYSTEM", "系统")))
        .enumOverride("sourceType", List.of(option("BUILTIN", "内置"), option("CUSTOM", "自定义")))
        .enumOverride("status", List.of(option("ACTIVE", "正常"), option("INACTIVE", "停用")))
        .enumOverride("source",
            List.of(option("MANUAL", "手动"), option("WECHAT", "微信"),
                option("SYSTEM_INIT", "系统初始化")))
        .columnOrder(
            List.of("id", "username", "realName", "mobile", "email", "userType", "sourceType",
                "source", "status",
                "createdAt")).build();
    this.pageSchema = generator.generate(IAM_USER, override);
  }

  private FilterOption option(String value, String label) {
    return new FilterOption().value(value).label(label);
  }

  public List<SelectFieldOrAsterisk> selectFields() {
    return List.of(IAM_USER.ID, IAM_USER.USER_NAME, IAM_USER.REAL_NAME, IAM_USER.MOBILE,
        IAM_USER.EMAIL,
        IAM_USER.USER_TYPE, IAM_USER.SOURCE_TYPE, IAM_USER.SOURCE, IAM_USER.CREATED_AT,
        IAM_USER.UPDATED_AT,
        IAM_USER.STATUS);
  }

  public TableLike<?> from() {
    return IAM_USER;
  }

  public Condition baseCondition() {
    return DSL.trueCondition();
  }

  public List<SortField<?>> defaultSorts() {
    return List.of(IAM_USER.ID.desc());
  }

  public RecordMapper<org.jooq.Record, User> rowMapper() {
    return record -> new User().id(record.get(IAM_USER.ID)).userName(record.get(IAM_USER.USER_NAME))
        .realName(record.get(IAM_USER.REAL_NAME)).mobile(record.get(IAM_USER.MOBILE))
        .email(record.get(IAM_USER.EMAIL))
        .userType(UserType.fromValue(record.get(IAM_USER.USER_TYPE)))
        .sourceType(SourceType.fromValue(record.get(IAM_USER.SOURCE_TYPE)))
        .source(record.get(IAM_USER.SOURCE))
        .createdAt(record.get(IAM_USER.CREATED_AT)).updatedAt(record.get(IAM_USER.UPDATED_AT))
        .status(UserStatus.fromValue(record.get(IAM_USER.STATUS)));
  }

  public JooqPageSchema pageSchema() {
    return pageSchema;
  }
}
