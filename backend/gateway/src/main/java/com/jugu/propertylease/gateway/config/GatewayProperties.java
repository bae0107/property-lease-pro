package com.jugu.propertylease.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * gateway.* 配置强类型绑定。
 *
 * <p>所有 Gateway 自有配置通过此类注入，禁止在 Gateway 模块内直接使用 {@code @Value} 读取 gateway.*（GW-C-09）。
 */
@Validated
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

  /**
   * 下游服务地址映射，key 为服务名（与路由路径第二段保持一致）
   */
  @NotNull
  private Map<String, RouteProperties> routes = Map.of();

  /**
   * 限流配置
   */
  @Valid
  @NotNull
  private RateLimitProperties rateLimit = new RateLimitProperties();

  /**
   * Header 清洗配置
   */
  @Valid
  @NotNull
  private SecurityHeaderProperties security = new SecurityHeaderProperties();

  /**
   * CORS 配置
   */
  @Valid
  @NotNull
  private CorsProperties cors = new CorsProperties();
  @Valid
  @NotNull
  private AuthVersionProperties authVersion = new AuthVersionProperties();

  // ----------------------------------------------------------------
  // 内嵌配置类
  // ----------------------------------------------------------------

  public Map<String, RouteProperties> getRoutes() {
    return routes;
  }

  public void setRoutes(Map<String, RouteProperties> routes) {
    this.routes = routes;
  }

  public RateLimitProperties getRateLimit() {
    return rateLimit;
  }

  public void setRateLimit(RateLimitProperties rateLimit) {
    this.rateLimit = rateLimit;
  }

  // ----------------------------------------------------------------
  // 根级别 getter / setter
  // ----------------------------------------------------------------

  public SecurityHeaderProperties getSecurity() {
    return security;
  }

  public void setSecurity(SecurityHeaderProperties security) {
    this.security = security;
  }

  public CorsProperties getCors() {
    return cors;
  }

  public void setCors(CorsProperties cors) {
    this.cors = cors;
  }

  public AuthVersionProperties getAuthVersion() {
    return authVersion;
  }

  public void setAuthVersion(AuthVersionProperties authVersion) {
    this.authVersion = authVersion;
  }

  /**
   * 单个下游服务地址配置
   */
  public static class RouteProperties {

    /**
     * 下游服务根地址，如 http://localhost:8081
     */
    @NotBlank
    private String url;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }

  /**
   * 限流配置
   */
  public static class RateLimitProperties {

    /**
     * 是否开启限流，默认 true；local 环境可设为 false
     */
    private boolean enabled = true;

    /**
     * 令牌桶容量（最大突发请求数），默认 100
     */
    @Positive
    private int capacity = 100;

    /**
     * 每秒补充令牌数（稳态请求速率），默认 20。 即：稳定状态下每 IP 每秒最多 20 个请求。
     */
    @Positive
    private int refillRate = 20;

    /**
     * 限流维度 key resolver，默认 ip。 可选值：ip（当前唯一实现），后续扩展 userId、apiKey 等。
     */
    private String keyResolver = "ip";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(int capacity) {
      this.capacity = capacity;
    }

    public int getRefillRate() {
      return refillRate;
    }

    public void setRefillRate(int refillRate) {
      this.refillRate = refillRate;
    }

    public String getKeyResolver() {
      return keyResolver;
    }

    public void setKeyResolver(String keyResolver) {
      this.keyResolver = keyResolver;
    }
  }

  /**
   * Header 清洗配置
   */
  public static class SecurityHeaderProperties {

    /**
     * 入站请求中需要移除的 Header 名称列表。 默认移除 X-Service-Token，防止外部伪造内部凭证（GW-C-05）。
     */
    private List<String> stripRequestHeaders = new ArrayList<>(List.of("X-Service-Token"));

    /**
     * 出站响应中需要移除的 Header 名称列表。 默认为空，后续按需添加。
     */
    private List<String> stripResponseHeaders = new ArrayList<>();

    public List<String> getStripRequestHeaders() {
      return stripRequestHeaders;
    }

    public void setStripRequestHeaders(List<String> stripRequestHeaders) {
      this.stripRequestHeaders = stripRequestHeaders;
    }

    public List<String> getStripResponseHeaders() {
      return stripResponseHeaders;
    }

    public void setStripResponseHeaders(List<String> stripResponseHeaders) {
      this.stripResponseHeaders = stripResponseHeaders;
    }
  }

  /**
   * CORS 配置
   */
  public static class CorsProperties {

    /**
     * 允许的 Origin 列表，各环境不同（GW-C-06）。 禁止配置 * 通配符，必须明确指定域名或地址。
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

    public List<String> getAllowedOrigins() {
      return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
      this.allowedOrigins = allowedOrigins;
    }
  }

  /**
   * 用户 token authVersion 校验配置。
   */
  public static class AuthVersionProperties {

    /**
     * 是否开启与 main-service 的 authVersion 一致性校验。
     */
    private boolean enabled = true;

    /**
     * 结果缓存时长（秒）。
     */
    @Positive
    private int cacheTtlSeconds = 10;

    /**
     * 调用 main-service 超时时间（毫秒）。
     */
    @Positive
    private int requestTimeoutMillis = 500;

    /**
     * 调用失败时是否放行，默认 false（fail-closed）。
     */
    private boolean failOpen = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getCacheTtlSeconds() {
      return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
      this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getRequestTimeoutMillis() {
      return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(int requestTimeoutMillis) {
      this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public boolean isFailOpen() {
      return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
      this.failOpen = failOpen;
    }
  }
}
