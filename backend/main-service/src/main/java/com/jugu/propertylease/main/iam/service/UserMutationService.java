package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.BatchUpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.CreateUserRequest;
import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.DataScopeType;
import com.jugu.propertylease.main.api.model.ResetUserPasswordRequest;
import com.jugu.propertylease.main.api.model.UpdateUserDataScopeRequest;
import com.jugu.propertylease.main.api.model.UpdateUserRolesRequest;
import com.jugu.propertylease.main.api.model.UpdateUserStatusRequest;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import com.jugu.propertylease.main.iam.repo.CredentialRepository;
import com.jugu.propertylease.main.iam.repo.IdentityRepository;
import com.jugu.propertylease.main.iam.repo.UserDataScopeRepository;
import com.jugu.propertylease.main.iam.repo.UserRepository;
import com.jugu.propertylease.main.iam.repo.UserRoleRepository;
import com.jugu.propertylease.main.iam.repo.model.RoleTypeSnapshot;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import com.jugu.propertylease.main.assetmgr.api.AssetHierarchyQueryPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户写操作聚合服务。
 *
 * <p>设计原则：
 * <ul>
 *   <li>每个业务操作是独立方法，只调用自己需要的 Repository，互不复用。</li>
 *   <li>前置校验（业务语义）在 Service 层；格式校验（非空/长度等）在 OpenAPI yaml + @Valid。</li>
 *   <li>事务边界在 Service 方法层，Repository 不加 @Transactional。</li>
 * </ul>
 */
@Service
public class UserMutationService {

    private final UserRepository userRepo;
    private final CredentialRepository credentialRepo;
    private final IdentityRepository identityRepo;
    private final UserRoleRepository userRoleRepo;
    private final UserDataScopeRepository userDataScopeRepo;
    private final AuthVersionService authVersionService;
    private final UserReadService userReadService;
    private final AssetHierarchyQueryPort assetHierarchyQueryPort;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    public UserMutationService(
            UserRepository userRepo,
            CredentialRepository credentialRepo,
            IdentityRepository identityRepo,
            UserRoleRepository userRoleRepo,
            UserDataScopeRepository userDataScopeRepo,
            AuthVersionService authVersionService,
            UserReadService userReadService,
            AssetHierarchyQueryPort assetHierarchyQueryPort) {
        this.userRepo = userRepo;
        this.credentialRepo = credentialRepo;
        this.identityRepo = identityRepo;
        this.userRoleRepo = userRoleRepo;
        this.userDataScopeRepo = userDataScopeRepo;
        this.authVersionService = authVersionService;
        this.userReadService = userReadService;
        this.assetHierarchyQueryPort = assetHierarchyQueryPort;
    }

    // ─────────────────────────────────────────────
    // 创建用户
    // ─────────────────────────────────────────────

    /**
     * 创建 STAFF / CONTRACTOR 用户。
     *
     * <p>格式校验（userType 非空、roleIds minItems:1、password minLength:8 等）
     * 由 OpenAPI yaml + @Valid 在 Controller 层完成，此处只做业务语义校验。
     */
    @Transactional
    public UserDetail createUser(CreateUserRequest request) {
        UserType userType = UserType.fromValue(request.getUserType().getValue());
        if (userType != UserType.STAFF && userType != UserType.CONTRACTOR) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_CREATE_TYPE_UNSUPPORTED", "仅支持创建 STAFF / CONTRACTOR 用户");
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1. 创建用户主体
        Long userId = userRepo.insert(userType, request.getUsername(), request.getRealName(),
                request.getMobile(), request.getEmail(), now);

        // 2. 写入密码凭证
        credentialRepo.upsert(userId, passwordEncoder.encode(request.getPassword()), now);

        // 3. 写入登录 Identity
        identityRepo.insertPasswordIdentity(userId, request.getUsername(), now);

        // 4. 校验并分配角色
        List<Long> roleIds = normalizeRoleIds(request.getRoleIds());
        validateRolesMatchUserType(roleIds, userType);
        userRoleRepo.replace(userId, roleIds, now);

        // 5. 校验并写入数据权限（选填）
        if (request.getScopes() != null) {
            validateScopeDimensionsMatchRoles(roleIds, request.getScopes());
            validateScopeResourcesExist(request.getScopes());
            userDataScopeRepo.replace(userId, request.getScopes(), now);
        }

        // 6. 触发 authVersion 递增，使后续生成的 token 携带最新版本
        authVersionService.bumpAuthVersion(userId, "CREATE_USER");

        return userReadService.getUserDetail(userId);
    }

    // ─────────────────────────────────────────────
    // 修改单个用户状态
    // ─────────────────────────────────────────────

    @Transactional
    public UserDetail updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        UserBaseInfo base = requireMutableUser(userId);
        // base 变量保留用于潜在的审计日志扩展，当前版本不作额外用途
//        _ = base;

        OffsetDateTime now = OffsetDateTime.now();
        userRepo.updateStatus(userId, request.getStatus(), now);
        authVersionService.bumpAuthVersion(userId, "UPDATE_STATUS");
        return userReadService.getUserDetail(userId);
    }

    // ─────────────────────────────────────────────
    // 批量修改用户状态
    // ─────────────────────────────────────────────

    /**
     * 批量更新用户状态，单条 SQL 完成，不产生 N+1。
     *
//     * <p>格式校验（ids 非空、status 非空）由 yaml @NotEmpty 在框架层完成。
     *
     * <p>注意：此方法不做每个用户的前置校验（isMutableType / isBuiltin），
     * 若需严格校验，应在业务场景层面通过查询接口先过滤，而非在批量写入时逐条查。
     * 若未来需要，可在此处增加一次 IN 查询统一校验。
     */
    @Transactional
    public void batchUpdateUserStatus(BatchUpdateUserStatusRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        userRepo.batchUpdateStatus(request.getIds(), request.getStatus(), now);

        // authVersion 批量递增：每个用户独立 bump，保证 token 失效语义正确
        // 若性能有压力，可改为批量 UPDATE auth_version = auth_version + 1 WHERE id IN (...)
        for (Long userId : request.getIds()) {
            authVersionService.bumpAuthVersion(userId, "BATCH_UPDATE_STATUS");
        }
    }

    // ─────────────────────────────────────────────
    // 重置密码
    // ─────────────────────────────────────────────

    @Transactional
    public UserDetail resetUserPassword(Long userId, ResetUserPasswordRequest request) {
        requireMutableUser(userId);

        OffsetDateTime now = OffsetDateTime.now();
        credentialRepo.upsert(userId, passwordEncoder.encode(request.getPassword()), now);
        authVersionService.bumpAuthVersion(userId, "RESET_PASSWORD");
        return userReadService.getUserDetail(userId);
    }

    // ─────────────────────────────────────────────
    // 修改用户角色
    // ─────────────────────────────────────────────

    @Transactional
    public UserDetail updateUserRoles(Long userId, UpdateUserRolesRequest request) {
        UserBaseInfo base = requireMutableUser(userId);

        List<Long> roleIds = normalizeRoleIds(request.getRoleIds());
        validateRolesMatchUserType(roleIds, base.userType());

        // 角色变更后，校验现有数据权限维度是否仍与新角色匹配
        List<DataScopeItem> currentScopes = userReadService.getUserDataScopeItems(userId);
        if (!currentScopes.isEmpty()) {
            validateScopeDimensionsMatchRoles(roleIds, currentScopes);
        }

        OffsetDateTime now = OffsetDateTime.now();
        userRoleRepo.replace(userId, roleIds, now);
        authVersionService.bumpAuthVersion(userId, "UPDATE_ROLES");
        return userReadService.getUserDetail(userId);
    }

    // ─────────────────────────────────────────────
    // 修改用户数据权限
    // ─────────────────────────────────────────────

    @Transactional
    public UserDataScope updateUserDataScope(Long userId, UpdateUserDataScopeRequest request) {
        requireMutableUser(userId);

        List<Long> roleIds = userRoleRepo.findRoleIdsByUserId(userId);
        List<DataScopeItem> scopes = request.getScopes() != null ? request.getScopes() : List.of();

        validateScopeDimensionsMatchRoles(roleIds, scopes);
        validateScopeResourcesExist(scopes);

        userDataScopeRepo.replace(userId, scopes, OffsetDateTime.now());

        // 数据权限变更影响查询范围，需递增 authVersion
        authVersionService.bumpAuthVersion(userId, "UPDATE_DATA_SCOPE");

        return userReadService.getUserDataScope(userId);
    }

    // ─────────────────────────────────────────────
    // 私有校验方法
    // ─────────────────────────────────────────────

    /**
     * 查询用户基础信息，并校验：用户存在、类型为 STAFF/CONTRACTOR、非 BUILTIN 来源。
     *
     * <p>所有写操作的前置门卫，返回 UserBaseInfo 供调用方按需使用。
     */
    private UserBaseInfo requireMutableUser(Long userId) {
        UserBaseInfo base = userRepo.findActiveBaseById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在"));

        if (!base.isMutableType()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_TYPE_UNSUPPORTED", "当前接口仅支持 STAFF / CONTRACTOR 用户");
        }
        if (base.isBuiltin()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_BUILTIN_FORBIDDEN", "BUILTIN 用户不允许修改");
        }
        return base;
    }

    /**
     * 去重并校验角色 ID 列表。
     *
     * <p>仅保留业务语义校验：角色 ID 不能为负数。
     * 非空校验已由 yaml minItems:1 + @Valid 在框架层完成，此处不重复。
     */
    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        List<Long> normalized = new ArrayList<>(new LinkedHashSet<>(roleIds));
        if (normalized.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_ROLE_ID_INVALID", "角色 ID 必须为正整数");
        }
        return normalized;
    }

    /**
     * 校验所选角色全部与用户类型匹配。
     *
     * <p>同时校验角色 ID 是否全部有效（数量一致则全部存在）。
     */
    private void validateRolesMatchUserType(List<Long> roleIds, UserType userType) {
        List<RoleTypeSnapshot> snapshots = userRoleRepo.findSnapshotsByIds(roleIds);

        if (snapshots.size() != roleIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_ROLE_NOT_FOUND", "所选角色包含无效 ID");
        }

        boolean hasMismatch = snapshots.stream()
                .anyMatch(r -> !r.matchesUserType(userType));
        if (hasMismatch) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_ROLE_TYPE_MISMATCH", "所选角色必须全部与用户类型一致");
        }
    }

    /**
     * 校验数据权限维度与角色要求一致。
     *
     * <p>只有角色要求了数据权限维度时才进行校验，
     * 无数据权限要求的角色（required_data_scope_dimension IS NULL）跳过。
     */
    private void validateScopeDimensionsMatchRoles(List<Long> roleIds, List<DataScopeItem> scopes) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        Set<String> requiredDimensions = userRoleRepo.findRequiredScopeDimensionsByIds(roleIds);
        if (requiredDimensions.isEmpty()) {
            return;
        }

        if (scopes == null || scopes.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_SCOPE_REQUIRED", "所选角色要求配置数据权限");
        }

        // SPECIFIC 类型校验 resourceIds 非空
        for (DataScopeItem item : scopes) {
            if (item.getScopeType() == DataScopeType.SPECIFIC
                    && (item.getResourceIds() == null || item.getResourceIds().isEmpty())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "IAM_USER_SCOPE_RESOURCE_REQUIRED", "scopeType=SPECIFIC 时 resourceIds 必填");
            }
        }

        Set<String> payloadDimensions = new LinkedHashSet<>();
        for (DataScopeItem item : scopes) {
            payloadDimensions.add(item.getDimension().getValue());
        }

        if (!payloadDimensions.equals(requiredDimensions)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IAM_USER_SCOPE_DIMENSION_MISMATCH", "数据权限维度必须与角色要求一致");
        }
    }

    /**
     * 校验 SPECIFIC 类型的 resourceIds 在资产层级主数据中真实存在（未删除）。
     *
     * <p>AREA 维度查 area_info，STORE 维度查 store_info；存在缺失 ID 时 400。
     */
    private void validateScopeResourcesExist(List<DataScopeItem> scopes) {
        if (scopes == null) {
            return;
        }
        for (DataScopeItem item : scopes) {
            if (item.getScopeType() != DataScopeType.SPECIFIC
                    || item.getResourceIds() == null || item.getResourceIds().isEmpty()) {
                continue;
            }
            Set<Long> requested = new LinkedHashSet<>(item.getResourceIds());
            Set<Long> existing = item.getDimension() == DataScopeDimension.AREA
                    ? assetHierarchyQueryPort.findExistingAreaIds(requested)
                    : assetHierarchyQueryPort.findExistingStoreIds(requested);
            Set<Long> missing = requested.stream()
                    .filter(id -> !existing.contains(id))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!missing.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "IAM_USER_SCOPE_RESOURCE_NOT_FOUND",
                        "数据权限资源不存在（" + item.getDimension().getValue() + "）：" + missing);
            }
        }
    }
}
