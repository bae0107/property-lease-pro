package com.jugu.propertylease.security.autoconfigure;

/**
 * 非 local 模式（service / gateway）下 jwt.service.secret 配置的存在性验证载体。
 *
 * <p>仅在 mode=service 或 mode=gateway 时创建，创建失败说明配置缺失，应用启动中止。
 * 设计模式与 {@link UserJwtValidator} 完全对称：用 Bean 创建失败来实现 "必填配置缺失 → 快速失败" 语义，比 JSR-303 约束更清晰。
 */
public class ServiceJwtValidator {

  private final String serviceSecret;

  public ServiceJwtValidator(String serviceSecret) {
    this.serviceSecret = serviceSecret;
  }

  public String getServiceSecret() {
    return serviceSecret;
  }
}
