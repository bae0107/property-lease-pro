package com.jugu.propertylease.main.iam.bootstrap;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.RoleType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.repo.IamPermissionManifestRepository;
import com.jugu.propertylease.main.iam.repo.model.UserDataScopeSeed;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动后从 manifest 同步权限全集、BUILTIN 角色和默认用户（fail-fast）。
 */
@Component
public class PermissionManifestBootstrap {

  private static final String SYSTEM_ROLE_CODE = "ROLE_SYSTEM";

  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;
  private final IamPermissionManifestRepository permissionManifestRepository;

  @Value("${iam.permissions.manifest-location:classpath:/iam/permissions-manifest.json}")
  private String manifestLocation;

  @Value("${iam.bootstrap.users.iam-admin-initial-password:}")
  private String iamAdminInitialPassword;

  @Value("${iam.bootstrap.users.reset-iam-admin-password-on-sync:false}")
  private boolean resetIamAdminPasswordOnSync;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public PermissionManifestBootstrap(ObjectMapper objectMapper,
      ResourceLoader resourceLoader, IamPermissionManifestRepository permissionManifestRepository) {
    this.objectMapper = objectMapper;
    this.resourceLoader = resourceLoader;
    this.permissionManifestRepository = permissionManifestRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void syncOnStartup() {
    Manifest manifest = loadManifest();
    String checksum = checksum(manifest.rawJson());
    String currentChecksum = permissionManifestRepository.findCurrentManifestChecksum();
    if (checksum.equals(currentChecksum)) {
      return;
    }

    syncPermissions(manifest.permissions());
    syncBuiltinRoles(manifest.builtinRoles());
    syncBuiltinUsers(manifest.builtinUsers());

    OffsetDateTime now = OffsetDateTime.now();
    permissionManifestRepository.upsertManifestSyncState(manifest.version(), checksum, now);
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
    Set<String> dbCodes = permissionManifestRepository.findAllPermissionCodes();

    for (ManifestPermission item : byCode.values()) {
      PermissionCode parsed = parseCode(item.code);
      permissionManifestRepository.upsertPermission(
          item.code,
          item.name == null ? item.code : item.name,
          item.description,
          parsed.resource(),
          parsed.action(),
          now);
    }

    for (String dbCode : dbCodes) {
      if (!byCode.containsKey(dbCode)) {
        permissionManifestRepository.markPermissionDeleted(dbCode, now);
      }
    }
  }

  private void syncBuiltinRoles(List<ManifestRole> builtinRoles) {
    if (builtinRoles.isEmpty()) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    for (ManifestRole role : builtinRoles) {
      Long roleId = permissionManifestRepository.findRoleIdByCode(role.code);
      String roleType = role.roleType == null ? RoleType.STAFF.getValue() : role.roleType;
      if (roleId == null) {
        roleId = permissionManifestRepository.insertBuiltinRole(
            role.name,
            role.code,
            roleType,
            role.requiredDataScopeDimension,
            role.description,
            now);
      } else {
        permissionManifestRepository.updateBuiltinRole(
            roleId,
            role.name,
            roleType,
            role.requiredDataScopeDimension,
            role.description,
            now);
      }

      List<String> declaredPermissions = role.permissions == null ? List.of() : role.permissions;
      if (SYSTEM_ROLE_CODE.equals(role.code) && !declaredPermissions.isEmpty()) {
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
            "IAM_BOOTSTRAP_SYSTEM_ROLE_PERMISSION_FORBIDDEN",
            "ROLE_SYSTEM 不能绑定任何权限");
      }
      Set<Long> permissionIds = new LinkedHashSet<>();
      for (String permissionCode : declaredPermissions) {
        Long permissionId = permissionManifestRepository.findActivePermissionIdByCode(permissionCode);
        if (permissionId == null) {
          throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
              "IAM_BOOTSTRAP_PERMISSION_MISSING",
              "BUILTIN 角色关联的权限不存在或已删除: " + permissionCode);
        }
        permissionIds.add(permissionId);
      }

      permissionManifestRepository.replaceRolePermissions(roleId, permissionIds);
    }
  }

  private void syncBuiltinUsers(List<ManifestUser> builtinUsers) {
    if (builtinUsers.isEmpty()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    for (ManifestUser user : builtinUsers) {
      Long userId = permissionManifestRepository.findUserIdByUserName(user.userName);

      if (userId == null) {
        if (user.mobile == null || user.mobile.isBlank()) {
          throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
              "IAM_BOOTSTRAP_BUILTIN_USER_MOBILE_REQUIRED",
              "内置用户缺少 mobile: " + user.userName);
        }
        userId = permissionManifestRepository.insertBuiltinUser(
            user.userType == null ? UserType.STAFF.getValue() : user.userType,
            user.status == null ? UserStatus.ACTIVE.getValue() : user.status,
            user.userName,
            user.realName,
            user.mobile,
            user.email,
            user.source == null ? SourceType.BUILTIN.getValue() : user.source,
            now);
      } else {
        permissionManifestRepository.updateBuiltinUserStatus(
            userId,
            user.status == null ? UserStatus.ACTIVE.getValue() : user.status,
            now);
      }

      if (user.roleCodes != null) {
        List<Long> roleIds = new ArrayList<>();
        for (String roleCode : user.roleCodes) {
          Long roleId = permissionManifestRepository.findRoleIdByCode(roleCode);
          if (roleId == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                "IAM_BOOTSTRAP_BUILTIN_ROLE_MISSING", "内置用户关联角色不存在: " + roleCode);
          }
          roleIds.add(roleId);
        }

        permissionManifestRepository.replaceUserRoles(userId, roleIds, now);
      }

      syncDefaultDataScopes(userId, user.defaultDataScopes, now);
      syncCredentialAndIdentity(user, userId, now);
    }
  }


  private void syncDefaultDataScopes(Long userId, List<DefaultDataScope> defaultDataScopes,
      OffsetDateTime now) {
    if (defaultDataScopes == null || defaultDataScopes.isEmpty()) {
      return;
    }
    List<UserDataScopeSeed> scopes = defaultDataScopes.stream()
        .map(scope -> new UserDataScopeSeed(scope.dimension, scope.scopeType, null))
        .toList();
    permissionManifestRepository.replaceUserDataScopes(userId, scopes, now);
  }

  private void syncCredentialAndIdentity(ManifestUser user, Long userId, OffsetDateTime now) {
    if (Boolean.TRUE.equals(user.createPasswordCredential)) {
      String rawPassword = resolveBootstrapPassword(user);
      boolean credentialExists = permissionManifestRepository.credentialExists(userId);

      if (!credentialExists) {
        if (rawPassword == null || rawPassword.isBlank()) {
          throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
              "IAM_BOOTSTRAP_ADMIN_PASSWORD_REQUIRED",
              "iam_admin 尚未预制密码，请配置 iam.bootstrap.users.iam-admin-initial-password");
        }
        String hash = passwordEncoder.encode(rawPassword);
        permissionManifestRepository.insertCredential(userId, hash, now);
      } else if (resetIamAdminPasswordOnSync && rawPassword != null && !rawPassword.isBlank()) {
        String hash = passwordEncoder.encode(rawPassword);
        permissionManifestRepository.updateCredential(userId, hash, now);
      }
    }

    if (Boolean.TRUE.equals(user.createPasswordIdentity)) {
      boolean identityExists = permissionManifestRepository.activePasswordIdentityExists(user.userName);
      if (!identityExists) {
        permissionManifestRepository.insertPasswordIdentity(userId, user.userName, now);
      }
    }
  }

  private String resolveBootstrapPassword(ManifestUser user) {
    if (user.initialPassword != null && !user.initialPassword.isBlank()) {
      return user.initialPassword;
    }
    if (iamAdminInitialPassword != null && !iamAdminInitialPassword.isBlank()) {
      return iamAdminInitialPassword;
    }
    return null;
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
    public String source;
    public String mobile;
    public String email;
    public String initialPassword;
    public Boolean createPasswordCredential;
    public Boolean createPasswordIdentity;
    public List<DefaultDataScope> defaultDataScopes;
    public List<String> roleCodes;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class DefaultDataScope {
    public String dimension;
    public String scopeType;
  }
}
