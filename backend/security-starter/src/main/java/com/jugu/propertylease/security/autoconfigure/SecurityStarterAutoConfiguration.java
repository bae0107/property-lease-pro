package com.jugu.propertylease.security.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.security.audit.AuditLogAspect;
import com.jugu.propertylease.security.audit.AuditLogger;
import com.jugu.propertylease.security.interceptor.ServiceTokenClientInterceptor;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Security Starter 总自动装配入口，始终装配公共 Bean。
 *
 * <p>装配矩阵：
 * <ul>
 *   <li>始终：JwtTokenParser, ServiceTokenGenerator, ServiceTokenClientInterceptor</li>
 *   <li>mode=service：ServiceJwtValidator（校验 jwt.service.secret 是否配置）</li>
 *   <li>mode=gateway：ServiceJwtValidator + UserJwtValidator（校验两个 secret）</li>
 *   <li>mode=mock：两个 Validator 均不创建，jwt 配置可完全省略</li>
 *   <li>存在 AuditLogger Bean：AuditLogAspect（三种 mode 均激活）</li>
 *   <li>SERVLET + mode=service：由 ServletSecurityAutoConfiguration 负责</li>
 *   <li>SERVLET + mode=mock：由 MockServletSecurityAutoConfiguration 负责</li>
 *   <li>REACTIVE + mode=gateway：由 ReactiveSecurityAutoConfiguration 负责</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityStarterAutoConfiguration {

  @Bean
  public JwtTokenParser jwtTokenParser() {
    return new JwtTokenParser();
  }

  @Bean
  public ServiceTokenGenerator serviceTokenGenerator() {
    return new ServiceTokenGenerator();
  }

  @Bean
  public ServiceTokenClientInterceptor serviceTokenClientInterceptor(
      SecurityProperties properties,
      ServiceTokenGenerator serviceTokenGenerator) {
    return new ServiceTokenClientInterceptor(properties, serviceTokenGenerator);
  }

  /**
   * mode=service 时校验 jwt.service.secret 是否配置。 缺失则抛 {@link BeanCreationException}，应用启动中止。
   */
  @Bean
  @ConditionalOnProperty(name = "security.mode", havingValue = "service")
  public ServiceJwtValidator serviceJwtValidatorForServiceMode(SecurityProperties properties) {
    return buildServiceJwtValidator(properties);
  }

  /**
   * mode=gateway 时校验 jwt.service.secret 是否配置。 缺失则抛 {@link BeanCreationException}，应用启动中止。
   */
  @Bean
  @ConditionalOnProperty(name = "security.mode", havingValue = "gateway")
  public ServiceJwtValidator serviceJwtValidatorForGatewayMode(SecurityProperties properties) {
    return buildServiceJwtValidator(properties);
  }

  /**
   * mode=gateway 时校验 jwt.user.secret 是否配置。 缺失则抛 {@link BeanCreationException}，应用启动中止。
   */
  @Bean
  @ConditionalOnProperty(name = "security.mode", havingValue = "gateway")
  public UserJwtValidator userJwtValidator(SecurityProperties properties) {
    if (properties.getJwt().getUser() == null
        || !StringUtils.hasText(properties.getJwt().getUser().getSecret())) {
      throw new BeanCreationException(
          "security.jwt.user.secret is required when security.mode=gateway");
    }
    return new UserJwtValidator(properties.getJwt().getUser().getSecret());
  }

  /**
   * 仅当 Spring Context 中存在 {@link AuditLogger} 实现 Bean 时激活审计切面。 三种 mode（service / gateway / mock）均支持
   * AuditLog。
   */
  @Bean
  @ConditionalOnBean(AuditLogger.class)
  public AuditLogAspect auditLogAspect(AuditLogger auditLogger, ObjectMapper objectMapper) {
    return new AuditLogAspect(auditLogger, objectMapper);
  }

  // ===== private =====

  private ServiceJwtValidator buildServiceJwtValidator(SecurityProperties properties) {
    if (properties.getJwt() == null
        || properties.getJwt().getService() == null
        || !StringUtils.hasText(properties.getJwt().getService().getSecret())) {
      throw new BeanCreationException(
          "security.jwt.service.secret is required when security.mode="
              + properties.getMode());
    }
    return new ServiceJwtValidator(properties.getJwt().getService().getSecret());
  }
}
