package com.jugu.propertylease.main.iam.service.mapper;

import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.User;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUserDataScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UserDtoMapper {

  private final RoleDtoMapper roleDtoMapper;

  public UserDtoMapper(RoleDtoMapper roleDtoMapper) {
    this.roleDtoMapper = roleDtoMapper;
  }

  public UserDetail toUserDetail(IamUser user, List<IamRole> roles, List<IamUserDataScope> scopeRows) {
    List<Role> roleDtos = roles.stream().map(roleDtoMapper::toRole).toList();
    UserDataScope userDataScope = new UserDataScope().scopes(toDataScopeItems(scopeRows));

    User base = new User()
        .id(user.getId())
        .userName(user.getUserName())
        .realName(user.getRealName())
        .mobile(user.getMobile())
        .email(user.getEmail())
        .userType(UserType.fromValue(user.getUserType()))
        .sourceType(SourceType.fromValue(user.getSourceType()))
        .status(UserStatus.fromValue(user.getStatus()))
        .source(user.getSource())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .authVersion(user.getAuthVersion())
        .deletedAt(user.getDeletedAt())
        .roleNames(roleDtos.isEmpty() ? "-" : String.join(",", roleDtos.stream().map(Role::getName).toList()));

    return new UserDetail()
        .id(base.getId())
        .userName(base.getUserName())
        .realName(base.getRealName())
        .mobile(base.getMobile())
        .email(base.getEmail())
        .userType(base.getUserType())
        .sourceType(base.getSourceType())
        .status(base.getStatus())
        .source(base.getSource())
        .createdAt(base.getCreatedAt())
        .updatedAt(base.getUpdatedAt())
        .authVersion(base.getAuthVersion())
        .deletedAt(base.getDeletedAt())
        .roleNames(base.getRoleNames())
        .roles(roleDtos)
        .dataScope(userDataScope);
  }

  private List<DataScopeItem> toDataScopeItems(List<IamUserDataScope> scopeRows) {
    List<DataScopeItem> scopes = new ArrayList<>();
    Map<String, List<Long>> specificMap = new LinkedHashMap<>();
    Map<String, Boolean> allMap = new LinkedHashMap<>();

    scopeRows.forEach(ds -> {
      String dim = ds.getScopeDimension();
      if ("ALL".equals(ds.getScopeType())) {
        allMap.put(dim, true);
      } else {
        specificMap.computeIfAbsent(dim, key -> new ArrayList<>());
        if (ds.getResourceId() != null) {
          specificMap.get(dim).add(ds.getResourceId());
        }
      }
    });

    for (String dim : unionKeys(allMap, specificMap)) {
      DataScopeItem item = new DataScopeItem()
          .dimension(com.jugu.propertylease.main.api.model.DataScopeDimension.fromValue(dim));
      if (Boolean.TRUE.equals(allMap.get(dim))) {
        item.scopeType(com.jugu.propertylease.main.api.model.DataScopeType.ALL).resourceIds(null);
      } else {
        item.scopeType(com.jugu.propertylease.main.api.model.DataScopeType.SPECIFIC)
            .resourceIds(specificMap.getOrDefault(dim, List.of()));
      }
      scopes.add(item);
    }
    return scopes;
  }

  private List<String> unionKeys(Map<String, Boolean> allMap, Map<String, List<Long>> specificMap) {
    List<String> keys = new ArrayList<>(allMap.keySet());
    for (String key : specificMap.keySet()) {
      if (!keys.contains(key)) {
        keys.add(key);
      }
    }
    return keys;
  }
}
