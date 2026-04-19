package com.jugu.propertylease.gateway.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS 配置（GW-4）。
 *
 * <p>在 Gateway 层统一处理跨域，下游微服务禁止配置 CORS（避免重复 Header，GW-C-06）。
 * allowedOrigins 从 {@link GatewayProperties} 读取，各环境不同。
 */
@Configuration
public class CorsConfig {

  private final GatewayProperties properties;

  public CorsConfig(GatewayProperties properties) {
    this.properties = properties;
  }

  @Bean
  public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();

    // 允许的 Origin：来自配置，各环境不同，禁止配置 * 通配符（GW-C-06）
    config.setAllowedOrigins(properties.getCors().getAllowedOrigins());

    // 允许全部 HTTP 方法
    config.setAllowedMethods(List.of(
        HttpMethod.GET.name(),
        HttpMethod.POST.name(),
        HttpMethod.PUT.name(),
        HttpMethod.PATCH.name(),
        HttpMethod.DELETE.name(),
        HttpMethod.OPTIONS.name()
    ));

    // 允许全部请求 Header
    config.setAllowedHeaders(List.of("*"));

    // 不允许携带 Cookie / Credentials
    config.setAllowCredentials(false);

    // 预检请求缓存时间 1 小时
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }
}
