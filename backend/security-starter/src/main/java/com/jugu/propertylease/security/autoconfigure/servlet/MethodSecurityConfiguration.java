package com.jugu.propertylease.security.autoconfigure.servlet;

import com.jugu.propertylease.security.authorization.SecurityPermissionEvaluator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * {@code @EnableMethodSecurity} 隔离配置类，确保整个应用只激活一次方法安全。
 *
 * <p>{@code @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)}：
 * 若其他配置已注册 ExpressionHandler（说明方法安全已激活），则跳过此类，避免重复。
 *
 * <p>微服务约束：
 * <ul>
 *   <li>禁止自定义 {@code @EnableMethodSecurity} 注解类（由 starter 统一提供）</li>
 *   <li>禁止自定义 {@code MethodSecurityExpressionHandler} Bean（会触发跳过条件）</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
public class MethodSecurityConfiguration {

  @Bean
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      SecurityPermissionEvaluator permissionEvaluator) {
    DefaultMethodSecurityExpressionHandler handler =
        new DefaultMethodSecurityExpressionHandler();
    handler.setPermissionEvaluator(permissionEvaluator);
    return handler;
  }
}
