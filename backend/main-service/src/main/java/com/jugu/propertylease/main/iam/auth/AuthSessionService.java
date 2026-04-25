package com.jugu.propertylease.main.iam.auth;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.LogoutRequest;
import com.jugu.propertylease.main.api.model.RefreshResult;
import com.jugu.propertylease.main.api.model.RefreshTokenRequest;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.iam.repo.IamAuthQueryRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import com.jugu.propertylease.security.properties.SecurityProperties;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh / logout 相关会话服务。
 */
@Service
public class AuthSessionService {

  private final IamAuthQueryRepository authQueryRepository;
  private final UserJwtIssuer userJwtIssuer;
  private final RefreshTokenService refreshTokenService;
  private final SecurityProperties securityProperties;

  public AuthSessionService(IamAuthQueryRepository authQueryRepository,
      UserJwtIssuer userJwtIssuer,
      RefreshTokenService refreshTokenService,
      SecurityProperties securityProperties) {
    this.authQueryRepository = authQueryRepository;
    this.userJwtIssuer = userJwtIssuer;
    this.refreshTokenService = refreshTokenService;
    this.securityProperties = securityProperties;
  }

  @Transactional
  public RefreshResult refresh(RefreshTokenRequest request) {
    if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_AUTH_REFRESH_TOKEN_REQUIRED",
          "refreshToken 不能为空");
    }

    RefreshTokenService.TokenPayload payload = refreshTokenService.validate(request.getRefreshToken());

    IamUser user = authQueryRepository.findActiveUserById(payload.userId())
        .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_INVALID",
            "refresh token 无效"));

    if (!UserStatus.ACTIVE.getValue().equals(user.getStatus())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "IAM_USER_ACCOUNT_DISABLED", "账号已禁用");
    }

    List<String> permissions = authQueryRepository.findPermissionCodesByUserId(payload.userId());

    int expirationSeconds = securityProperties.getJwt().getUser().getExpiration();
    String accessToken = userJwtIssuer.issue(
        payload.userId(),
        payload.username(),
        permissions,
        user.getAuthVersion(),
        securityProperties.getJwt().getUser().getSecret(),
        expirationSeconds);

    String newRefreshToken = refreshTokenService.rotate(
        request.getRefreshToken(),
        payload.userId(),
        payload.username(),
        payload.userType());

    return new RefreshResult()
        .accessToken(accessToken)
        .refreshToken(newRefreshToken)
        .expiresIn(expirationSeconds);
  }

  @Transactional
  public void logout(LogoutRequest request) {
    if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_AUTH_REFRESH_TOKEN_REQUIRED",
          "refreshToken 不能为空");
    }
    refreshTokenService.revoke(request.getRefreshToken());
  }
}
