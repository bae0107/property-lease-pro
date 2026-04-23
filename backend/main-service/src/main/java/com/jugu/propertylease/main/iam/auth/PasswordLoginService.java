package com.jugu.propertylease.main.iam.auth;

import static com.jugu.propertylease.main.jooq.Tables.IAM_CREDENTIAL;
import static com.jugu.propertylease.main.jooq.Tables.IAM_IDENTITY;
import static com.jugu.propertylease.main.jooq.Tables.IAM_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_ROLE_PERMISSION;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER;
import static com.jugu.propertylease.main.jooq.Tables.IAM_USER_ROLE;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.LoginResult;
import com.jugu.propertylease.main.api.model.PasswordLoginRequest;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.security.properties.SecurityProperties;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户名密码登录。
 */
@Service
public class PasswordLoginService {

  private final DSLContext dsl;
  private final UserJwtIssuer userJwtIssuer;
  private final RefreshTokenService refreshTokenService;
  private final SecurityProperties securityProperties;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public PasswordLoginService(DSLContext dsl,
      UserJwtIssuer userJwtIssuer,
      RefreshTokenService refreshTokenService,
      SecurityProperties securityProperties) {
    this.dsl = dsl;
    this.userJwtIssuer = userJwtIssuer;
    this.refreshTokenService = refreshTokenService;
    this.securityProperties = securityProperties;
  }

  @Transactional
  public LoginResult login(PasswordLoginRequest request) {
    if (request == null || blank(request.getUsername()) || blank(request.getPassword())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_AUTH_REQUEST_INVALID", "用户名和密码不能为空");
    }

    Record identity = dsl.selectFrom(IAM_IDENTITY)
        .where(IAM_IDENTITY.PROVIDER.eq("password"))
        .and(IAM_IDENTITY.PROVIDER_USER_ID.eq(request.getUsername().trim()))
        .and(IAM_IDENTITY.DELETED_AT.isNull())
        .fetchOne();

    if (identity == null) {
      throw invalidCredentials();
    }

    Long userId = identity.get(IAM_IDENTITY.USER_ID);
    Record user = dsl.selectFrom(IAM_USER)
        .where(IAM_USER.ID.eq(userId))
        .and(IAM_USER.DELETED_AT.isNull())
        .fetchOne();

    if (user == null) {
      throw invalidCredentials();
    }

    String userType = user.get(IAM_USER.USER_TYPE);
    if (!"STAFF".equals(userType) && !"CONTRACTOR".equals(userType)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "IAM_AUTH_LOGIN_METHOD_NOT_ALLOWED",
          "当前账号不支持用户名密码登录");
    }

    if (!"ACTIVE".equals(user.get(IAM_USER.STATUS))) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "IAM_USER_ACCOUNT_DISABLED", "账号已禁用");
    }

    Record credential = dsl.selectFrom(IAM_CREDENTIAL)
        .where(IAM_CREDENTIAL.USER_ID.eq(userId))
        .fetchOne();

    if (credential == null || !passwordEncoder.matches(request.getPassword(),
        credential.get(IAM_CREDENTIAL.PASSWORD_HASH))) {
      throw invalidCredentials();
    }

    List<String> permissions = dsl.selectDistinct(IAM_PERMISSION.CODE)
        .from(IAM_USER_ROLE)
        .join(IAM_ROLE_PERMISSION).on(IAM_USER_ROLE.ROLE_ID.eq(IAM_ROLE_PERMISSION.ROLE_ID))
        .join(IAM_PERMISSION).on(IAM_ROLE_PERMISSION.PERMISSION_ID.eq(IAM_PERMISSION.ID))
        .where(IAM_USER_ROLE.USER_ID.eq(userId))
        .and(IAM_PERMISSION.DELETED_AT.isNull())
        .fetch(IAM_PERMISSION.CODE);

    int expirationSeconds = securityProperties.getJwt().getUser().getExpiration();
    String accessToken = userJwtIssuer.issue(
        userId,
        user.get(IAM_USER.USER_NAME),
        permissions,
        user.get(IAM_USER.AUTH_VERSION),
        securityProperties.getJwt().getUser().getSecret(),
        expirationSeconds);

    String refreshToken = refreshTokenService.create(
        userId,
        user.get(IAM_USER.USER_NAME),
        userType);

    return new LoginResult()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(expirationSeconds)
        .userId(userId)
        .userType(UserType.fromValue(userType));
  }

  private BusinessException invalidCredentials() {
    return new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
