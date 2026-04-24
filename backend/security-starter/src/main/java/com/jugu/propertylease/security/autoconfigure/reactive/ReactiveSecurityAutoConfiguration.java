package com.jugu.propertylease.security.autoconfigure.reactive;

import com.jugu.propertylease.security.autoconfigure.SecurityStarterAutoConfiguration;
import com.jugu.propertylease.security.filter.reactive.ReactiveUserJwtFilter;
import com.jugu.propertylease.security.filter.reactive.UserTokenVersionChecker;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway（WebFlux）安全自动装配。
 *
 * <p>只在 {@code security.mode=gateway} 时激活（Reactive WebFlux 应用）。
 * 认证完全由 {@link ReactiveUserJwtFilter} 负责，SecurityWebFilterChain 仅关闭默认行为。
 */
@AutoConfiguration(after = SecurityStarterAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(name = "security.mode", havingValue = "gateway")
@EnableWebFluxSecurity
public class ReactiveSecurityAutoConfiguration {

  @Bean
  @Order(-100)
  public ReactiveUserJwtFilter reactiveUserJwtFilter(JwtTokenParser jwtTokenParser,
      ServiceTokenGenerator serviceTokenGenerator,
      SecurityProperties properties,
      org.springframework.beans.factory.ObjectProvider<UserTokenVersionChecker> versionCheckerProvider) {
    UserTokenVersionChecker checker = versionCheckerProvider.getIfAvailable(() -> (userId, authVersion) -> true);
    return new ReactiveUserJwtFilter(jwtTokenParser, serviceTokenGenerator, properties, checker);
  }

  @Bean
  public SecurityWebFilterChain reactiveGatewaySecurityFilterChain(ServerHttpSecurity http) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .authorizeExchange(auth -> auth.anyExchange().permitAll())
        .build();
  }
}
