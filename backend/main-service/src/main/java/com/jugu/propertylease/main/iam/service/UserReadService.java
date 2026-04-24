package com.jugu.propertylease.main.iam.service;

import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_DATA_SCOPE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.User;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 用户读取聚合服务。
 */
@Service
public class UserReadService {

  private final DSLContext dsl;

  public UserReadService(DSLContext dsl) {
    this.dsl = dsl;
  }

  public UserDetail getUserDetail(Long userId) {
    Record record = dsl.selectFrom(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();
    if (record == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在");
    }

    List<Role> roles = dsl.select(IAM_ROLE.fields())
        .from(IAM_USER_ROLE)
        .join(IAM_ROLE).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE.ID))
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .fetch(r -> new Role()
            .id(r.get(IAM_ROLE.ID))
            .name(r.get(IAM_ROLE.NAME))
            .code(r.get(IAM_ROLE.CODE))
            .roleType(RoleType.fromValue(r.get(IAM_ROLE.ROLE_TYPE)))
            .sourceType(SourceType.fromValue(r.get(IAM_ROLE.SOURCE_TYPE)))
            .requiredDataScopeDimension(EnumValueMapper.nullableFromValue(r.get(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION),
                com.jugu.propertylease.main.api.model.DataScopeDimension::fromValue))
            .description(r.get(IAM_ROLE.DESCRIPTION))
            .createdAt(r.get(IAM_ROLE.CREATED_AT))
            .updatedAt(r.get(IAM_ROLE.UPDATED_AT)));

    List<DataScopeItem> scopes = new ArrayList<>();
    Map<String, List<Long>> specificMap = new LinkedHashMap<>();
    Map<String, Boolean> allMap = new LinkedHashMap<>();
    dsl.selectFrom(IAM_USER_DATA_SCOPE)
        .where(IAM_USER_DATA_SCOPE.USER_ID.eq(userId))
        .fetch()
        .forEach(ds -> {
          String dim = ds.getScopeDimension();
          if ("ALL".equals(ds.getScopeType())) {
            allMap.put(dim, true);
          } else {
            specificMap.computeIfAbsent(dim, k -> new ArrayList<>());
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

    UserDataScope userDataScope = new UserDataScope().scopes(scopes);
    User base = new User()
        .id(record.get(IAM_USER.ID))
        .userName(record.get(IAM_USER.USER_NAME))
        .realName(record.get(IAM_USER.REAL_NAME))
        .mobile(record.get(IAM_USER.MOBILE))
        .email(record.get(IAM_USER.EMAIL))
        .userType(UserType.fromValue(record.get(IAM_USER.USER_TYPE)))
        .sourceType(SourceType.fromValue(record.get(IAM_USER.SOURCE_TYPE)))
        .status(UserStatus.fromValue(record.get(IAM_USER.STATUS)))
        .source(record.get(IAM_USER.SOURCE))
        .createdAt(record.get(IAM_USER.CREATED_AT))
        .updatedAt(record.get(IAM_USER.UPDATED_AT))
        .authVersion(record.get(IAM_USER.AUTH_VERSION))
        .deletedAt(record.get(IAM_USER.DELETED_AT))
        .roleNames(roles.isEmpty() ? "-" : String.join(",", roles.stream().map(Role::getName).toList()));

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
        .roles(roles)
        .dataScope(userDataScope);
  }

  private List<String> unionKeys(Map<String, Boolean> allMap, Map<String, List<Long>> specificMap) {
    List<String> keys = new ArrayList<>(allMap.keySet());
    for (String k : specificMap.keySet()) {
      if (!keys.contains(k)) {
        keys.add(k);
      }
    }
    return keys;
  }
}
