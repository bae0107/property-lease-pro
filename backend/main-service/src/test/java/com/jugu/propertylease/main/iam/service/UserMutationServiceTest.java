package com.jugu.propertylease.main.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UpdateUserRolesRequest;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import com.jugu.propertylease.main.iam.repo.UserRepository;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
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
  private AuthVersionService authVersionService;

  @Mock
  private UserReadService userReadService;

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
