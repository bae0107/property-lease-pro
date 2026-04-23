package com.jugu.propertylease.main.iam.auth;

import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.LogoutRequest;
import com.jugu.propertylease.main.api.model.RefreshResult;
import com.jugu.propertylease.main.api.model.RefreshTokenRequest;
import com.jugu.propertylease.security.properties.SecurityProperties;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh / logout 相关会话服务。
 */
@Service
public class AuthSessionService {

  private final DSLContext dsl;
  private final UserJwtIssuer userJwtIssuer;
  private final RefreshTokenService refreshTokenService;
  private final SecurityProperties securityProperties;

  public AuthSessionService(DSLContext dsl,
      UserJwtIssuer userJwtIssuer,
      RefreshTokenService refreshTokenService,
      SecurityProperties securityProperties) {
    this.dsl = dsl;
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

    Record user = dsl.selectFrom(IAM_USER)
        .where(IAM_USER.ID.eq(payload.userId()))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();
    if (user == null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_INVALID",
          "refresh token 无效");
    }

    String status = user.get(IAM_USER.STATUS);
    if (!"ACTIVE".equals(status)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "IAM_USER_ACCOUNT_DISABLED", "账号已禁用");
    }

    List<String> permissions = dsl.selectDistinct(IAM_PERMISSION.CODE)
        .from(IAM_USER_ROLE)
        .join(IAM_ROLE_PERMISSION).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE_PERMISSION.ROLE_ID))
        .join(IAM_PERMISSION).on(IAM_ROLE_PERMISSION.PERMISSION_ID.eq(IAM_PERMISSION.ID))
        .where(IAM_USER_ROLE.USER_ID.eq(payload.userId()))
        .and(IAM_PERMISSION.DELETED_AT.isNull())
        .fetch(IAM_PERMISSION.CODE);

    int expirationSeconds = securityProperties.getJwt().getUser().getExpiration();
    String accessToken = userJwtIssuer.issue(
        payload.userId(),
        payload.username(),
        permissions,
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
