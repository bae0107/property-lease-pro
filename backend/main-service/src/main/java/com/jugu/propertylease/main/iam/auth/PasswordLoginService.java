package com.jugu.propertylease.main.iam.auth;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.LoginResult;
import com.jugu.propertylease.main.api.model.PasswordLoginRequest;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.repo.IamAuthQueryRepository;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import com.jugu.propertylease.security.properties.SecurityProperties;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户名密码登录。
 */
@Service
public class PasswordLoginService {

  private final IamAuthQueryRepository authQueryRepository;
  private final UserJwtIssuer userJwtIssuer;
  private final RefreshTokenService refreshTokenService;
  private final SecurityProperties securityProperties;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public PasswordLoginService(IamAuthQueryRepository authQueryRepository,
      UserJwtIssuer userJwtIssuer,
      RefreshTokenService refreshTokenService,
      SecurityProperties securityProperties) {
    this.authQueryRepository = authQueryRepository;
    this.userJwtIssuer = userJwtIssuer;
    this.refreshTokenService = refreshTokenService;
    this.securityProperties = securityProperties;
  }

  @Transactional
  public LoginResult login(PasswordLoginRequest request) {
    if (request == null || blank(request.getUsername()) || blank(request.getPassword())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IAM_AUTH_REQUEST_INVALID", "用户名和密码不能为空");
    }

    Long userId = authQueryRepository.findActivePasswordIdentityUserId(request.getUsername().trim())
        .orElseThrow(this::invalidCredentials);

    IamUser user = authQueryRepository.findActiveUserById(userId)
        .orElseThrow(this::invalidCredentials);

    String userType = user.getUserType();
    if (!UserType.STAFF.getValue().equals(userType) && !UserType.CONTRACTOR.getValue().equals(userType)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "IAM_AUTH_LOGIN_METHOD_NOT_ALLOWED",
          "当前账号不支持用户名密码登录");
    }

    if (!UserStatus.ACTIVE.getValue().equals(user.getStatus())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "IAM_USER_ACCOUNT_DISABLED", "账号已禁用");
    }

    String passwordHash = authQueryRepository.findCredentialByUserId(userId)
        .map(c -> c.getPasswordHash())
        .orElse(null);
    if (passwordHash == null || !passwordEncoder.matches(request.getPassword(), passwordHash)) {
      throw invalidCredentials();
    }

    List<String> permissions = authQueryRepository.findPermissionCodesByUserId(userId);

    int expirationSeconds = securityProperties.getJwt().getUser().getExpiration();
    String accessToken = userJwtIssuer.issue(
        userId,
        user.getUserName(),
        permissions,
        user.getAuthVersion(),
        securityProperties.getJwt().getUser().getSecret(),
        expirationSeconds);

    String refreshToken = refreshTokenService.create(
        userId,
        user.getUserName(),
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
