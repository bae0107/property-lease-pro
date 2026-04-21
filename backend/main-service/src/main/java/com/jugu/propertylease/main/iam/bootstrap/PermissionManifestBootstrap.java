package com.jugu.propertylease.main.iam.bootstrap;

import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;
import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION_SYNC_STATE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动后从 manifest 同步权限全集、BUILTIN 角色和默认用户（fail-fast）。
 */
@Component
public class PermissionManifestBootstrap {

  private final DSLContext dsl;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;

  @Value("${iam.permissions.manifest-location:classpath:/iam/permissions-manifest.json}")
  private String manifestLocation;

  public PermissionManifestBootstrap(DSLContext dsl, ObjectMapper objectMapper,
      ResourceLoader resourceLoader) {
    this.dsl = dsl;
    this.objectMapper = objectMapper;
    this.resourceLoader = resourceLoader;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void syncOnStartup() {
    Manifest manifest = loadManifest();
    String checksum = checksum(manifest.rawJson());
    String currentChecksum = dsl.select(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM)
        .from(IAM_PERMISSION_SYNC_STATE)
        .where(IAM_PERMISSION_SYNC_STATE.ID.eq(1L))
        .fetchOne(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM);
    if (checksum.equals(currentChecksum)) {
      return;
    }

    syncPermissions(manifest.permissions());
    syncBuiltinRoles(manifest.builtinRoles());
    syncBuiltinUsers(manifest.builtinUsers());

    OffsetDateTime now = OffsetDateTime.now();
    if (currentChecksum == null) {
      dsl.insertInto(IAM_PERMISSION_SYNC_STATE)
          .set(IAM_PERMISSION_SYNC_STATE.ID, 1L)
          .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_VERSION, manifest.version())
          .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM, checksum)
          .set(IAM_PERMISSION_SYNC_STATE.SYNCED_AT, now)
          .execute();
    } else {
      dsl.update(IAM_PERMISSION_SYNC_STATE)
          .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_VERSION, manifest.version())
          .set(IAM_PERMISSION_SYNC_STATE.MANIFEST_CHECKSUM, checksum)
          .set(IAM_PERMISSION_SYNC_STATE.SYNCED_AT, now)
          .where(IAM_PERMISSION_SYNC_STATE.ID.eq(1L))
          .execute();
    }
  }

  private Manifest loadManifest() {
    try {
      Resource resource = resourceLoader.getResource(manifestLocation);
      if (!resource.exists()) {
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "IAM_BOOTSTRAP_MANIFEST_MISSING",
            "权限 manifest 不存在: " + manifestLocation);
      }
      String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      ManifestPayload payload = objectMapper.readValue(json, ManifestPayload.class);
      return new Manifest(json, payload.version == null ? "v1" : payload.version,
          payload.permissions == null ? List.of() : payload.permissions,
          payload.builtinRoles == null ? List.of() : payload.builtinRoles,
          payload.builtinUsers == null ? List.of() : payload.builtinUsers);
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "IAM_BOOTSTRAP_MANIFEST_INVALID",
          "权限 manifest 解析失败: " + ex.getMessage());
    }
  }

  private void syncPermissions(List<ManifestPermission> permissions) {
    Map<String, ManifestPermission> byCode = new LinkedHashMap<>();
    for (ManifestPermission permission : permissions) {
      byCode.put(permission.code, permission);
    }

    OffsetDateTime now = OffsetDateTime.now();
    Set<String> dbCodes = new LinkedHashSet<>(dsl.select(IAM_PERMISSION.CODE).from(IAM_PERMISSION)
        .fetch(IAM_PERMISSION.CODE));

    for (ManifestPermission item : byCode.values()) {
      PermissionCode parsed = parseCode(item.code);
      if (dbCodes.contains(item.code)) {
        dsl.update(IAM_PERMISSION)
            .set(IAM_PERMISSION.NAME, item.name == null ? item.code : item.name)
            .set(IAM_PERMISSION.DESCRIPTION, item.description)
            .set(IAM_PERMISSION.RESOURCE, parsed.resource())
            .set(IAM_PERMISSION.ACTION, parsed.action())
            .set(IAM_PERMISSION.DELETED_AT, (OffsetDateTime) null)
            .set(IAM_PERMISSION.UPDATED_AT, now)
            .where(IAM_PERMISSION.CODE.eq(item.code))
            .execute();
      } else {
        dsl.insertInto(IAM_PERMISSION)
            .set(IAM_PERMISSION.CODE, item.code)
            .set(IAM_PERMISSION.NAME, item.name == null ? item.code : item.name)
            .set(IAM_PERMISSION.RESOURCE, parsed.resource())
            .set(IAM_PERMISSION.ACTION, parsed.action())
            .set(IAM_PERMISSION.DESCRIPTION, item.description)
            .set(IAM_PERMISSION.DELETED_AT, (OffsetDateTime) null)
            .set(IAM_PERMISSION.CREATED_AT, now)
            .set(IAM_PERMISSION.UPDATED_AT, now)
            .execute();
      }
    }

    for (String dbCode : dbCodes) {
      if (!byCode.containsKey(dbCode)) {
        dsl.update(IAM_PERMISSION)
            .set(IAM_PERMISSION.DELETED_AT, now)
            .set(IAM_PERMISSION.UPDATED_AT, now)
            .where(IAM_PERMISSION.CODE.eq(dbCode))
            .execute();
      }
    }
  }

  private void syncBuiltinRoles(List<ManifestRole> builtinRoles) {
    if (builtinRoles.isEmpty()) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    Map<String, Long> roleIdByCode = new LinkedHashMap<>();

    for (ManifestRole role : builtinRoles) {
      Long roleId = dsl.select(IAM_ROLE.ID).from(IAM_ROLE).where(IAM_ROLE.CODE.eq(role.code))
          .fetchOne(IAM_ROLE.ID);
      if (roleId == null) {
        roleId = dsl.insertInto(IAM_ROLE)
            .set(IAM_ROLE.NAME, role.name)
            .set(IAM_ROLE.CODE, role.code)
            .set(IAM_ROLE.ROLE_TYPE, role.roleType == null ? "STAFF" : role.roleType)
            .set(IAM_ROLE.SOURCE_TYPE, "BUILTIN")
            .set(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION, role.requiredDataScopeDimension)
            .set(IAM_ROLE.DESCRIPTION, role.description)
            .set(IAM_ROLE.CREATED_AT, now)
            .set(IAM_ROLE.UPDATED_AT, now)
            .returning(IAM_ROLE.ID)
            .fetchOne(IAM_ROLE.ID);
      } else {
        dsl.update(IAM_ROLE)
            .set(IAM_ROLE.NAME, role.name)
            .set(IAM_ROLE.ROLE_TYPE, role.roleType == null ? "STAFF" : role.roleType)
            .set(IAM_ROLE.SOURCE_TYPE, "BUILTIN")
            .set(IAM_ROLE.REQUIRED_DATA_SCOPE_DIMENSION, role.requiredDataScopeDimension)
            .set(IAM_ROLE.DESCRIPTION, role.description)
            .set(IAM_ROLE.UPDATED_AT, now)
            .where(IAM_ROLE.ID.eq(roleId))
            .execute();
      }
      roleIdByCode.put(role.code, roleId);

      Set<Long> permissionIds = new LinkedHashSet<>();
      for (String permissionCode : role.permissions == null ? List.<String>of() : role.permissions) {
        Long permissionId = dsl.select(IAM_PERMISSION.ID)
            .from(IAM_PERMISSION)
            .where(IAM_PERMISSION.CODE.eq(permissionCode))
            .and(IAM_PERMISSION.DELETED_AT.isNull())
            .fetchOne(IAM_PERMISSION.ID);
        if (permissionId == null) {
          throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
              "IAM_BOOTSTRAP_PERMISSION_MISSING",
              "BUILTIN 角色关联的权限不存在或已删除: " + permissionCode);
        }
        permissionIds.add(permissionId);
      }

      dsl.deleteFrom(IAM_ROLE_PERMISSION).where(IAM_ROLE_PERMISSION.ROLE_ID.eq(roleId)).execute();
      for (Long permissionId : permissionIds) {
        dsl.insertInto(IAM_ROLE_PERMISSION)
            .set(IAM_ROLE_PERMISSION.ROLE_ID, roleId)
            .set(IAM_ROLE_PERMISSION.PERMISSION_ID, permissionId)
            .execute();
      }
    }
  }

  private void syncBuiltinUsers(List<ManifestUser> builtinUsers) {
    if (builtinUsers.isEmpty()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    for (ManifestUser user : builtinUsers) {
      Long userId = dsl.select(IAM_USER.ID).from(IAM_USER).where(IAM_USER.USER_NAME.eq(user.userName))
          .fetchOne(IAM_USER.ID);

      if (userId == null) {
        if (user.mobile == null || user.mobile.isBlank()) {
          throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
              "IAM_BOOTSTRAP_BUILTIN_USER_MOBILE_REQUIRED",
              "内置用户缺少 mobile: " + user.userName);
        }
        userId = dsl.insertInto(IAM_USER)
            .set(IAM_USER.USER_TYPE, user.userType == null ? "STAFF" : user.userType)
            .set(IAM_USER.SOURCE_TYPE, "BUILTIN")
            .set(IAM_USER.STATUS, user.status == null ? "ACTIVE" : user.status)
            .set(IAM_USER.AUTH_VERSION, 0)
            .set(IAM_USER.USER_NAME, user.userName)
            .set(IAM_USER.REAL_NAME, user.realName)
            .set(IAM_USER.MOBILE, user.mobile)
            .set(IAM_USER.EMAIL, user.email)
            .set(IAM_USER.SOURCE, "MANIFEST")
            .set(IAM_USER.CREATED_AT, now)
            .set(IAM_USER.UPDATED_AT, now)
            .returning(IAM_USER.ID)
            .fetchOne(IAM_USER.ID);
      } else {
        dsl.update(IAM_USER)
            .set(IAM_USER.STATUS, user.status == null ? "ACTIVE" : user.status)
            .set(IAM_USER.UPDATED_AT, now)
            .where(IAM_USER.ID.eq(userId))
            .execute();
      }

      if (user.roleCodes != null) {
        List<Long> roleIds = new ArrayList<>();
        for (String roleCode : user.roleCodes) {
          Long roleId = dsl.select(IAM_ROLE.ID)
              .from(IAM_ROLE)
              .where(IAM_ROLE.CODE.eq(roleCode))
              .fetchOne(IAM_ROLE.ID);
          if (roleId == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                "IAM_BOOTSTRAP_BUILTIN_ROLE_MISSING", "内置用户关联角色不存在: " + roleCode);
          }
          roleIds.add(roleId);
        }

        dsl.deleteFrom(IAM_USER_ROLE).where(IAM_USER_ROLE.USER_ID.eq(userId)).execute();
        for (Long roleId : roleIds) {
          dsl.insertInto(IAM_USER_ROLE)
              .set(IAM_USER_ROLE.USER_ID, userId)
              .set(IAM_USER_ROLE.ROLE_ID, roleId)
              .set(IAM_USER_ROLE.CREATED_AT, now)
              .execute();
        }
      }
    }
  }

  private PermissionCode parseCode(String code) {
    if (code == null || !code.contains(":")) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "IAM_BOOTSTRAP_PERMISSION_CODE_INVALID",
          "非法权限码: " + code);
    }
    int index = code.lastIndexOf(':');
    if (index <= 0 || index >= code.length() - 1) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "IAM_BOOTSTRAP_PERMISSION_CODE_INVALID",
          "非法权限码: " + code);
    }
    return new PermissionCode(code.substring(0, index), code.substring(index + 1));
  }

  private String checksum(String rawJson) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(rawJson.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 计算失败", ex);
    }
  }

  private record Manifest(String rawJson, String version, List<ManifestPermission> permissions,
                          List<ManifestRole> builtinRoles, List<ManifestUser> builtinUsers) {
  }

  private record PermissionCode(String resource, String action) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ManifestPayload {
    public String version;
    public List<ManifestPermission> permissions;
    public List<ManifestRole> builtinRoles;
    public List<ManifestUser> builtinUsers;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ManifestPermission {
    public String code;
    public String name;
    public String description;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ManifestRole {
    public String code;
    public String name;
    public String description;
    public String roleType;
    public String requiredDataScopeDimension;
    public List<String> permissions;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ManifestUser {
    public String userName;
    public String realName;
    public String userType;
    public String status;
    public String mobile;
    public String email;
    public List<String> roleCodes;
  }
}
