package com.jugu.propertylease.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.gateway.config.GatewayProperties;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.filter.reactive.UserTokenVersionChecker;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 调用 main-service 内部接口校验用户 token 的 authVersion 是否最新。
 */
@Component
public class MainServiceUserTokenVersionChecker implements UserTokenVersionChecker {

  private static final Logger log = LoggerFactory.getLogger(MainServiceUserTokenVersionChecker.class);

  private final GatewayProperties.AuthVersionProperties authVersionProperties;
  private final String checkEndpoint;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final ServiceTokenGenerator serviceTokenGenerator;
  private final String serviceName;
  private final String serviceJwtSecret;
  private final int serviceJwtExpirationSeconds;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public MainServiceUserTokenVersionChecker(GatewayProperties gatewayProperties,
      SecurityProperties securityProperties,
      ServiceTokenGenerator serviceTokenGenerator,
      ObjectMapper objectMapper) {
    this.authVersionProperties = gatewayProperties.getAuthVersion();
    this.objectMapper = objectMapper;
    String mainServiceUrl = gatewayProperties.getRoutes().get("main-service").getUrl();
    this.checkEndpoint = mainServiceUrl + "/internal/v1/auth/version/check";
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(authVersionProperties.getRequestTimeoutMillis()))
        .build();
    this.serviceTokenGenerator = serviceTokenGenerator;
    this.serviceName = securityProperties.getServiceName();
    this.serviceJwtSecret = securityProperties.getJwt().getService().getSecret();
    this.serviceJwtExpirationSeconds = securityProperties.getJwt().getService().getExpiration();
  }

  @Override
  public boolean isCurrent(Long userId, Integer authVersion) {
    if (!authVersionProperties.isEnabled()) {
      return true;
    }
    if (userId == null || userId <= 0 || authVersion == null || authVersion < 0) {
      return false;
    }
    String key = userId + ":" + authVersion;
    long now = System.currentTimeMillis();
    CacheEntry cached = cache.get(key);
    if (cached != null && cached.expireAtMillis() > now) {
      return cached.current();
    }

    boolean current = queryMainService(userId, authVersion);
    long expireAt = now + authVersionProperties.getCacheTtlSeconds() * 1000L;
    cache.put(key, new CacheEntry(current, expireAt));
    return current;
  }

  private boolean queryMainService(Long userId, Integer authVersion) {
    try {
      String uri = checkEndpoint
          + "?userId=" + URLEncoder.encode(userId.toString(), StandardCharsets.UTF_8)
          + "&authVersion=" + URLEncoder.encode(authVersion.toString(), StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
          .timeout(Duration.ofMillis(authVersionProperties.getRequestTimeoutMillis()))
          .header(SecurityConstants.HEADER_SERVICE_TOKEN, generateServiceToken())
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.warn("authVersion check failed with status={}, userId={}, authVersion={}",
            response.statusCode(), userId, authVersion);
        return authVersionProperties.isFailOpen();
      }
      JsonNode body = objectMapper.readTree(response.body());
      return body.path("current").asBoolean(false);
    } catch (Exception ex) {
      log.warn("authVersion check request error, userId={}, authVersion={}, failOpen={}",
          userId, authVersion, authVersionProperties.isFailOpen(), ex);
      return authVersionProperties.isFailOpen();
    }
  }

  private String generateServiceToken() {
    return serviceTokenGenerator.generate(serviceName, null, List.of(), serviceJwtSecret,
        serviceJwtExpirationSeconds);
  }

  private record CacheEntry(boolean current, long expireAtMillis) {
  }
}
