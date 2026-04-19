package com.jugu.propertylease.security.autoconfigure;

/**
 * Gateway 模式下 jwt.user.secret 配置的存在性验证载体。 此 Bean 仅在 mode=gateway 时创建；创建失败说明配置缺失，应用启动中止。
 */
public class UserJwtValidator {

  private final String userSecret;

  public UserJwtValidator(String userSecret) {
    this.userSecret = userSecret;
  }

  public String getUserSecret() {
    return userSecret;
  }
}
