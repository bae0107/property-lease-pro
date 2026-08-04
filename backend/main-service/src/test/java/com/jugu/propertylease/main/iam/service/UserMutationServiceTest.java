package com.jugu.propertylease.main.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.DataScopeType;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UpdateUserDataScopeRequest;
import com.jugu.propertylease.main.api.model.UpdateUserRolesRequest;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import com.jugu.propertylease.main.iam.repo.UserDataScopeRepository;
import com.jugu.propertylease.main.iam.repo.UserRepository;
import com.jugu.propertylease.main.iam.repo.UserRoleRepository;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import com.jugu.propertylease.main.assetmgr.api.AssetHierarchyQueryPort;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserMutationServiceTest {

  @Mock
  private UserRepository userMutationRepository;

  @Mock
  private UserRoleRepository userRoleRepo;

  @Mock
  private UserDataScopeRepository userDataScopeRepo;

  @Mock
  private AuthVersionService authVersionService;

  @Mock
  private UserReadService userReadService;

  @Mock
  private AssetHierarchyQueryPort assetHierarchyQueryPort;

  @InjectMocks
  private UserMutationService userMutationService;

  @Test
  void patchUser_builtinUser_shouldReject() {
    Long userId = 100L;
    when(userMutationRepository.findActiveBaseById(userId))
        .thenReturn(Optional.of(new UserBaseInfo(UserType.STAFF, SourceType.BUILTIN)));

    assertThatThrownBy(() -> userMutationService.updateUserRoles(userId, new UpdateUserRolesRequest()))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo("IAM_USER_BUILTIN_FORBIDDEN"));

    verifyNoInteractions(authVersionService, userReadService);
  }

  @Test
  void updateUserDataScope_specificWithMissingStore_shouldReject() {
    Long userId = 100L;
    when(userMutationRepository.findActiveBaseById(userId))
        .thenReturn(Optional.of(new UserBaseInfo(UserType.STAFF, SourceType.CUSTOM)));
    when(userRoleRepo.findRoleIdsByUserId(userId)).thenReturn(List.of(37L));
    when(userRoleRepo.findRequiredScopeDimensionsByIds(List.of(37L))).thenReturn(Set.of("STORE"));
    when(assetHierarchyQueryPort.findExistingStoreIds(anyCollection())).thenReturn(Set.of(1L));

    DataScopeItem item = new DataScopeItem()
        .dimension(DataScopeDimension.STORE)
        .scopeType(DataScopeType.SPECIFIC)
        .resourceIds(List.of(1L, 999L));
    UpdateUserDataScopeRequest request = new UpdateUserDataScopeRequest().scopes(List.of(item));

    assertThatThrownBy(() -> userMutationService.updateUserDataScope(userId, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo("IAM_USER_SCOPE_RESOURCE_NOT_FOUND"));

    verifyNoInteractions(userDataScopeRepo, authVersionService);
  }

  @Test
  void updateUserDataScope_specificWithMissingArea_shouldReject() {
    Long userId = 100L;
    when(userMutationRepository.findActiveBaseById(userId))
        .thenReturn(Optional.of(new UserBaseInfo(UserType.STAFF, SourceType.CUSTOM)));
    when(userRoleRepo.findRoleIdsByUserId(userId)).thenReturn(List.of(37L));
    when(userRoleRepo.findRequiredScopeDimensionsByIds(List.of(37L))).thenReturn(Set.of("AREA"));
    when(assetHierarchyQueryPort.findExistingAreaIds(anyCollection())).thenReturn(Set.of());

    DataScopeItem item = new DataScopeItem()
        .dimension(DataScopeDimension.AREA)
        .scopeType(DataScopeType.SPECIFIC)
        .resourceIds(List.of(7L));
    UpdateUserDataScopeRequest request = new UpdateUserDataScopeRequest().scopes(List.of(item));

    assertThatThrownBy(() -> userMutationService.updateUserDataScope(userId, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo("IAM_USER_SCOPE_RESOURCE_NOT_FOUND"));

    verifyNoInteractions(userDataScopeRepo, authVersionService);
  }

  @Test
  void updateUserDataScope_allResourcesExist_shouldPersist() {
    Long userId = 100L;
    when(userMutationRepository.findActiveBaseById(userId))
        .thenReturn(Optional.of(new UserBaseInfo(UserType.STAFF, SourceType.CUSTOM)));
    when(userRoleRepo.findRoleIdsByUserId(userId)).thenReturn(List.of(37L));
    when(userRoleRepo.findRequiredScopeDimensionsByIds(List.of(37L))).thenReturn(Set.of("STORE"));
    when(assetHierarchyQueryPort.findExistingStoreIds(anyCollection())).thenReturn(Set.of(1L, 2L));
    when(userReadService.getUserDataScope(userId)).thenReturn(new UserDataScope());

    DataScopeItem item = new DataScopeItem()
        .dimension(DataScopeDimension.STORE)
        .scopeType(DataScopeType.SPECIFIC)
        .resourceIds(List.of(1L, 2L));
    List<DataScopeItem> scopes = List.of(item);
    UpdateUserDataScopeRequest request = new UpdateUserDataScopeRequest().scopes(scopes);

    userMutationService.updateUserDataScope(userId, request);

    verify(userDataScopeRepo).replace(eq(userId), eq(scopes), any());
    verify(authVersionService).bumpAuthVersion(userId, "UPDATE_DATA_SCOPE");
  }

  @Test
  void updateUserDataScope_scopeAll_skipsResourceExistenceCheck() {
    Long userId = 100L;
    when(userMutationRepository.findActiveBaseById(userId))
        .thenReturn(Optional.of(new UserBaseInfo(UserType.STAFF, SourceType.CUSTOM)));
    when(userRoleRepo.findRoleIdsByUserId(userId)).thenReturn(List.of(37L));
    when(userRoleRepo.findRequiredScopeDimensionsByIds(List.of(37L))).thenReturn(Set.of("STORE"));
    when(userReadService.getUserDataScope(userId)).thenReturn(new UserDataScope());

    DataScopeItem item = new DataScopeItem()
        .dimension(DataScopeDimension.STORE)
        .scopeType(DataScopeType.ALL);
    UpdateUserDataScopeRequest request = new UpdateUserDataScopeRequest().scopes(List.of(item));

    userMutationService.updateUserDataScope(userId, request);

    verifyNoInteractions(assetHierarchyQueryPort);
  }

  @Test
  void printTestToken() {
    String secret = "dev-service-jwt-secret-for-development-only";
    String token = Jwts.builder()
        .setSubject("swagger-test")
        .claim("userId", 1L)
        .claim("permissions", List.of("iam:user:read", "iam:user:write",
            "iam:role:read", "iam:role:write",
            "iam:permission:read"))
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 86400_000L)) // 1天
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)),
            SignatureAlgorithm.HS256)
        .compact();
    System.out.println(token);
  }
}
