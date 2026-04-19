package com.jugu.propertylease.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API Gateway 启动入口。
 *
 * <p>WebFlux / Reactive 应用，通过 security-starter 自动装配
 * {@code ReactiveSecurityAutoConfiguration}（security.mode=gateway）。
 *
 * <p>{@code @ConfigurationPropertiesScan} 扫描并注册本包下所有
 * {@code @ConfigurationProperties} 类（如 {@code GatewayProperties}）为 Spring Bean。
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.jugu.propertylease.gateway")
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
