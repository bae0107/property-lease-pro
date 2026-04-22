package com.jugu.propertylease.build.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.yaml.snakeyaml.Yaml;

@Mojo(name = "generate-manifest", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class GenerateIamManifestMojo extends AbstractMojo {

  private static final Set<String> HTTP_METHODS = Set.of(
      "get", "post", "put", "delete", "patch", "options", "head", "trace");

  private static final Set<String> BUILTIN_PERMISSION_ROLES = Set.of("ADMIN", "TENANT", "CONTRACTOR");

  @Parameter(defaultValue = "${project.basedir}/src/main/resources/openapi", required = true)
  private Path openapiDir;

  @Parameter(defaultValue = "${project.build.directory}/generated-resources/iam/permissions-manifest.json", required = true)
  private Path outputFile;

  @Parameter
  private List<String> includeFileNameContains = List.of("external");

  @Parameter
  private List<String> authPathPrefixes = List.of("/auth");

  @Parameter(defaultValue = "ROLE_IAM_ADMIN")
  private String roleAdminCode;

  @Parameter(defaultValue = "ROLE_TENANT")
  private String roleTenantCode;

  @Parameter(defaultValue = "ROLE_CONTRACTOR")
  private String roleContractorCode;

  @Parameter(defaultValue = "ROLE_SYSTEM")
  private String roleSystemCode;

  @Parameter(defaultValue = "iam_admin")
  private String iamAdminUserName;

  @Parameter(defaultValue = "iam_system")
  private String systemUserName;

  @Parameter(defaultValue = "13800000001")
  private String iamAdminMobile;

  @Parameter(defaultValue = "13800000002")
  private String systemMobile;

  @Parameter(defaultValue = "iam_admin@builtin.local")
  private String iamAdminEmail;

  @Parameter(defaultValue = "iam_system@builtin.local")
  private String systemEmail;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (Files.notExists(openapiDir)) {
      throw new MojoFailureException("OpenAPI directory does not exist: " + openapiDir);
    }

    List<Path> yamlFiles = collectYamlFiles(openapiDir);
    if (yamlFiles.isEmpty()) {
      throw new MojoFailureException("No *external*.yaml files found under: " + openapiDir);
    }

    Map<String, PermissionDef> permissionMap = new LinkedHashMap<>();
    Map<String, Set<String>> roleToPermissionCodes = new LinkedHashMap<>();
    roleToPermissionCodes.put("ADMIN", new LinkedHashSet<>());
    roleToPermissionCodes.put("TENANT", new LinkedHashSet<>());
    roleToPermissionCodes.put("CONTRACTOR", new LinkedHashSet<>());

    for (Path yamlFile : yamlFiles) {
      parseYamlFile(yamlFile, permissionMap, roleToPermissionCodes);
    }

    Manifest manifest = buildManifest(permissionMap, roleToPermissionCodes, yamlFiles);
    writeManifest(manifest);

    getLog().info("Generated IAM manifest: " + outputFile + ", permissions="
        + manifest.permissions.size());
  }

  private List<Path> collectYamlFiles(Path root) throws MojoExecutionException {
    List<Path> files = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
          boolean isYaml = name.endsWith(".yaml") || name.endsWith(".yml");
          if (!isYaml) {
            return FileVisitResult.CONTINUE;
          }
          boolean contains = includeFileNameContains.stream()
              .map(v -> v.toLowerCase(Locale.ROOT))
              .anyMatch(name::contains);
          if (contains) {
            files.add(file);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to scan OpenAPI files", e);
    }
    files.sort(Comparator.comparing(Path::toString));
    return files;
  }

  @SuppressWarnings("unchecked")
  private void parseYamlFile(Path yamlPath, Map<String, PermissionDef> permissionMap,
      Map<String, Set<String>> roleToPermissionCodes) throws MojoFailureException, MojoExecutionException {
    Map<String, Object> root;
    try (InputStream is = Files.newInputStream(yamlPath)) {
      root = new Yaml().load(is);
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to parse yaml: " + yamlPath, e);
    }

    if (root == null) {
      return;
    }

    Object pathsObj = root.get("paths");
    if (!(pathsObj instanceof Map<?, ?> rawPaths)) {
      return;
    }

    for (Map.Entry<?, ?> pathEntry : rawPaths.entrySet()) {
      String apiPath = String.valueOf(pathEntry.getKey());
      if (!(pathEntry.getValue() instanceof Map<?, ?> methods)) {
        continue;
      }

      for (Map.Entry<?, ?> methodEntry : methods.entrySet()) {
        String method = String.valueOf(methodEntry.getKey()).toLowerCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(method)) {
          continue;
        }
        if (!(methodEntry.getValue() instanceof Map<?, ?> operation)) {
          continue;
        }

        boolean authApi = isAuthApi(apiPath);
        String permission = trimToNull(operation.get("x-required-permission"));

        if (!authApi && permission == null) {
          throw new MojoFailureException("Missing x-required-permission at "
              + yamlPath + " -> " + method.toUpperCase(Locale.ROOT) + " " + apiPath);
        }
        if (permission == null) {
          continue;
        }

        String name = trimToNull(operation.get("x-permission-name"));
        String description = trimToNull(operation.get("x-permission-description"));
        if (name == null) {
          name = permission;
        }
        if (description == null) {
          description = permission;
        }

        permissionMap.putIfAbsent(permission, new PermissionDef(permission, name, description));

        Set<String> builtinRoles = parseBuiltinRoles(operation.get("x-builtin-roles"), yamlPath, apiPath,
            method);
        for (String role : builtinRoles) {
          roleToPermissionCodes.get(role).add(permission);
        }
      }
    }
  }

  private boolean isAuthApi(String apiPath) {
    return authPathPrefixes.stream().anyMatch(apiPath::startsWith);
  }

  @SuppressWarnings("unchecked")
  private Set<String> parseBuiltinRoles(Object raw, Path yamlPath, String apiPath,
      String method) throws MojoFailureException {
    if (raw == null) {
      return Set.of();
    }

    Set<String> roles = new LinkedHashSet<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        addRole(roles, trimToNull(item), yamlPath, apiPath, method);
      }
    } else {
      String text = trimToNull(raw);
      if (text != null) {
        for (String token : text.split(",")) {
          String v = token.trim();
          if (!v.isEmpty()) {
            addRole(roles, v, yamlPath, apiPath, method);
          }
        }
      }
    }
    return roles;
  }

  private void addRole(Set<String> roles, String roleValue, Path yamlPath, String apiPath,
      String method) throws MojoFailureException {
    if (roleValue == null) {
      return;
    }
    String normalized = roleValue.trim().toUpperCase(Locale.ROOT);
    if (!BUILTIN_PERMISSION_ROLES.contains(normalized)) {
      throw new MojoFailureException("Invalid x-builtin-roles value '" + roleValue
          + "' at " + yamlPath + " -> " + method.toUpperCase(Locale.ROOT) + " " + apiPath
          + "; allowed values: " + BUILTIN_PERMISSION_ROLES);
    }
    roles.add(normalized);
  }

  private Manifest buildManifest(Map<String, PermissionDef> permissions,
      Map<String, Set<String>> roleToPermissionCodes, List<Path> files) throws MojoExecutionException {
    Manifest manifest = new Manifest();
    manifest.version = "v1";
    manifest.generatedAt = OffsetDateTime.now().toString();

    List<PermissionItem> permissionItems = permissions.values().stream()
        .sorted(Comparator.comparing(p -> p.code))
        .map(p -> {
          PermissionItem item = new PermissionItem();
          item.code = p.code;
          item.name = p.name;
          item.description = p.description;
          item.builtinRoles = roleToPermissionCodes.entrySet().stream()
              .filter(e -> e.getValue().contains(p.code))
              .map(Map.Entry::getKey)
              .sorted()
              .collect(Collectors.toList());
          return item;
        })
        .collect(Collectors.toList());
    manifest.permissions = permissionItems;

    BuiltinRole adminRole = role(roleAdminCode, "IAM管理员", "STAFF",
        roleToPermissionCodes.get("ADMIN"));
    BuiltinRole tenantRole = role(roleTenantCode, "租户角色", "TENANT",
        roleToPermissionCodes.get("TENANT"));
    BuiltinRole contractorRole = role(roleContractorCode, "外包角色", "CONTRACTOR",
        roleToPermissionCodes.get("CONTRACTOR"));
    BuiltinRole systemRole = role(roleSystemCode, "系统角色", "SYSTEM", Set.of());

    manifest.builtinRoles = List.of(adminRole, tenantRole, contractorRole, systemRole);

    BuiltinUser iamAdmin = user(iamAdminUserName, "IAM Admin", "STAFF", "ACTIVE", "BUILTIN",
        iamAdminMobile, iamAdminEmail, List.of(roleAdminCode));
    BuiltinUser system = user(systemUserName, "System", "SYSTEM", "ACTIVE", "BUILTIN",
        systemMobile, systemEmail, List.of(roleSystemCode));
    manifest.builtinUsers = List.of(iamAdmin, system);

    String digestSource = files.stream().map(Path::toString).sorted().collect(Collectors.joining("|"))
        + "|" + permissionItems.stream().map(i -> i.code + ":" + i.name + ":" + i.description)
        .collect(Collectors.joining("|"));
    manifest.checksum = sha256(digestSource);
    return manifest;
  }

  private BuiltinRole role(String code, String name, String roleType, Set<String> permissionCodes) {
    BuiltinRole role = new BuiltinRole();
    role.code = code;
    role.name = name;
    role.roleType = roleType;
    role.sourceType = "BUILTIN";
    role.requiredDataScopeDimension = null;
    role.description = name;
    role.permissions = permissionCodes == null ? List.of() : permissionCodes.stream().sorted().toList();
    return role;
  }

  private BuiltinUser user(String userName, String realName, String userType, String status,
      String source, String mobile, String email, List<String> roleCodes) {
    BuiltinUser user = new BuiltinUser();
    user.userName = userName;
    user.realName = realName;
    user.userType = userType;
    user.status = status;
    user.source = source;
    user.mobile = mobile;
    user.email = email;
    user.roleCodes = roleCodes;
    return user;
  }

  private void writeManifest(Manifest manifest) throws MojoExecutionException {
    try {
      Files.createDirectories(outputFile.getParent());
      ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
      Files.writeString(outputFile, mapper.writeValueAsString(manifest), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to write manifest: " + outputFile, e);
    }
  }

  private String trimToNull(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  private String sha256(String content) throws MojoExecutionException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new MojoExecutionException("Cannot compute SHA-256", e);
    }
  }

  private static final class PermissionDef {
    private final String code;
    private final String name;
    private final String description;

    private PermissionDef(String code, String name, String description) {
      this.code = code;
      this.name = name;
      this.description = description;
    }
  }

  private static final class Manifest {
    public String version;
    public String generatedAt;
    public String checksum;
    public List<PermissionItem> permissions = List.of();
    public List<BuiltinRole> builtinRoles = List.of();
    public List<BuiltinUser> builtinUsers = List.of();
  }

  private static final class PermissionItem {
    public String code;
    public String name;
    public String description;
    public List<String> builtinRoles = List.of();
  }

  private static final class BuiltinRole {
    public String code;
    public String name;
    public String roleType;
    public String sourceType;
    public String requiredDataScopeDimension;
    public String description;
    public List<String> permissions = List.of();
  }

  private static final class BuiltinUser {
    public String userName;
    public String realName;
    public String userType;
    public String status;
    public String source;
    public String mobile;
    public String email;
    public List<String> roleCodes = List.of();
  }
}
