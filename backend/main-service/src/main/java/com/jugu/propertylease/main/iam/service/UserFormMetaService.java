package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;

import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.ScopeOption;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserCreateFormMeta;
import com.jugu.propertylease.main.iam.page.options.UserScopeOptionProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

/**
 * 创建用户表单元数据服务。
 */
@Service
public class UserFormMetaService {

  private final DSLContext dsl;
  private final UserScopeOptionProvider scopeOptionProvider;

  public UserFormMetaService(DSLContext dsl, UserScopeOptionProvider scopeOptionProvider) {
    this.dsl = dsl;
    this.scopeOptionProvider = scopeOptionProvider;
  }

  public UserCreateFormMeta getCreateFormMeta() {
    List<Role> roleOptions = dsl.selectFrom(IAM_ROLE)
        .where(IAM_ROLE.ROLE_TYPE.eq("STAFF"))
        .fetch(r -> new Role()
            .id(r.getId())
            .name(r.getName())
            .code(r.getCode())
            .roleType(RoleType.fromValue(r.getRoleType()))
            .sourceType(SourceType.fromValue(r.getSourceType()))
            .requiredDataScopeDimension(EnumValueMapper.nullableFromValue(r.getRequiredDataScopeDimension(),
                com.jugu.propertylease.main.api.model.DataScopeDimension::fromValue))
            .description(r.getDescription())
            .createdAt(r.getCreatedAt())
            .updatedAt(r.getUpdatedAt()));

    List<ScopeOption> areaOptions = scopeOptionProvider.listAreas().stream()
        .map(it -> new ScopeOption().value(it.id()).label(it.label()))
        .toList();
    List<ScopeOption> storeOptions = scopeOptionProvider.listStores().stream()
        .map(it -> new ScopeOption().value(it.id()).label(it.label()))
        .toList();

    Map<String, List<Long>> mapping = new LinkedHashMap<>();
    for (Map.Entry<Long, List<Long>> e : scopeOptionProvider.allowedStoreIdsByAreaId().entrySet()) {
      mapping.put(String.valueOf(e.getKey()), e.getValue());
    }

    return new UserCreateFormMeta()
        .roleOptions(roleOptions)
        .areaOptions(areaOptions)
        .storeOptions(storeOptions)
        .allowedStoreIdsByAreaId(mapping);
  }
}

