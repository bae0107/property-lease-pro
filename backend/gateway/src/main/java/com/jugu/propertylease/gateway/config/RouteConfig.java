package com.jugu.propertylease.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Gateway 路由配置（GW-1）。
 *
 * <p>路由规则：外部路径 /api/{service-name}/** → 下游服务 /**，StripPrefix=2（去掉 /api/{service-name}）。
 *
 * <p>示例：
 * <pre>
 *   外部：/api/main-service/auth/login     → main-service:8081  /auth/login
 *   外部：/api/billing-service/orders      → billing-service:8082 /orders
 *   外部：/api/device-service/commands     → device-service:8083  /commands
 * </pre>
 *
 * <p>下游地址来自 {@link GatewayProperties}，禁止在代码中硬编码（GW-C-01）。
 * 新增下游服务只需在 gateway.routes.* 添加配置并在此类增加一条路由。
 */
@Configuration
public class RouteConfig {

  private static final int STRIP_PREFIX_COUNT = 2;

  private final String mainServiceUrl;
  private final String billingServiceUrl;
  private final String deviceServiceUrl;

  public RouteConfig(GatewayProperties properties) {
    // 在构造器中提前校验所有下游地址，启动时快速失败（GW-C-01）
    this.mainServiceUrl = resolveUrl(properties, "main-service");
    this.billingServiceUrl = resolveUrl(properties, "billing-service");
    this.deviceServiceUrl = resolveUrl(properties, "device-service");
  }

  /**
   * 从配置中获取下游服务地址，若未配置则抛出 IllegalStateException。
   */
  private static String resolveUrl(GatewayProperties properties, String serviceName) {
    GatewayProperties.RouteProperties route = properties.getRoutes().get(serviceName);
    if (route == null || route.getUrl() == null || route.getUrl().isBlank()) {
      throw new IllegalStateException(
          "gateway.routes." + serviceName + ".url 未配置，请检查 application.yml");
    }
    return route.getUrl();
  }

  @Bean
  public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
    return builder.routes()
        // main-service: /api/main-service/** → main-service /**
        .route("main-service",
            r -> r.path("/api/main-service/**")
                .filters(f -> f.stripPrefix(STRIP_PREFIX_COUNT))
                .uri(mainServiceUrl))
        // billing-service: /api/billing-service/** → billing-service /**
        .route("billing-service",
            r -> r.path("/api/billing-service/**")
                .filters(f -> f.stripPrefix(STRIP_PREFIX_COUNT))
                .uri(billingServiceUrl))
        // device-service: /api/device-service/** → device-service /**
        .route("device-service",
            r -> r.path("/api/device-service/**")
                .filters(f -> f.stripPrefix(STRIP_PREFIX_COUNT))
                .uri(deviceServiceUrl))
        .build();
  }
}
