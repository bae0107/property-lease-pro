package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.ScopeOption;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserCreateFormMeta;
import com.jugu.propertylease.main.iam.page.options.UserScopeOptionProvider;
import com.jugu.propertylease.main.iam.repo.IamUserFormMetaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 创建用户表单元数据服务。
 */
@Service
public class UserFormMetaService {

  private final IamUserFormMetaRepository userFormMetaRepository;
  private final UserScopeOptionProvider scopeOptionProvider;

  public UserFormMetaService(IamUserFormMetaRepository userFormMetaRepository,
      UserScopeOptionProvider scopeOptionProvider) {
    this.userFormMetaRepository = userFormMetaRepository;
    this.scopeOptionProvider = scopeOptionProvider;
  }

  public UserCreateFormMeta getCreateFormMeta() {
    List<Role> roleOptions = userFormMetaRepository.findStaffRoles().stream()
        .map(r -> new Role()
            .id(r.getId())
            .name(r.getName())
            .code(r.getCode())
            .roleType(RoleType.fromValue(r.getRoleType()))
            .sourceType(SourceType.fromValue(r.getSourceType()))
            .requiredDataScopeDimension(EnumValueMapper.nullableFromValue(r.getRequiredDataScopeDimension(),
                com.jugu.propertylease.main.api.model.DataScopeDimension::fromValue))
            .description(r.getDescription())
            .createdAt(r.getCreatedAt())
            .updatedAt(r.getUpdatedAt()))
        .toList();

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
