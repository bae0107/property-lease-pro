package com.jugu.propertylease.security.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * JWT 密钥与过期时间配置（User JWT 和 Service JWT 共用此结构）。
 */
public class JwtProperties {

  /**
   * HS256 密钥原文，生产环境通过环境变量注入
   */
  @NotBlank(message = "JWT secret must not be blank")
  private String secret;

  /**
   * Token 有效期，单位：秒
   */
  @Min(value = 1, message = "JWT expiration must be at least 1 second")
  private int expiration;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public int getExpiration() {
    return expiration;
  }

  public void setExpiration(int expiration) {
    this.expiration = expiration;
  }
}
