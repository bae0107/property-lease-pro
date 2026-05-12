package com.jugu.propertylease.main.iam.service.mapper;

import com.jugu.propertylease.main.api.model.Permission;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.iam.service.EnumValueMapper;
import com.jugu.propertylease.main.jooq.tables.pojos.IamPermission;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import org.springframework.stereotype.Component;

/**
 * 角色相关 jOOQ POJO → OpenAPI DTO 转换器。
 *
 */
@Component
public class RoleDtoMapper {

  public Role toRole(IamRole role) {
    return new Role()
        .id(role.getId())
        .name(role.getName())
        .code(role.getCode())
        .roleType(RoleType.fromValue(role.getRoleType()))
        .sourceType(SourceType.fromValue(role.getSourceType()))
        // requiredDataScopeDimension 可为 null（无数据权限要求的角色）
        .requiredDataScopeDimension(
            EnumValueMapper.nullableFromValue(role.getRequiredDataScopeDimension(),
                com.jugu.propertylease.main.api.model.DataScopeDimension::fromValue))
        .description(role.getDescription())
        .createdAt(role.getCreatedAt())
        .updatedAt(role.getUpdatedAt());
  }

  public Permission toPermission(IamPermission permission) {
    return new Permission()
        .id(permission.getId())
        .code(permission.getCode())
        .name(permission.getName())
        .resource(permission.getResource())
        .action(permission.getAction())
        .description(permission.getDescription());
  }

}
