package com.jugu.propertylease.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.gateway.config.GatewayProperties;
import com.jugu.propertylease.main.client.internal.api.InternalAuthApiClient;
import com.jugu.propertylease.main.client.internal.model.AuthVersionCheckResult;
import com.jugu.propertylease.security.constants.SecurityConstants;
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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 基于 main-service internal URL 的 InternalAuthApiClient 实现。
 */
@Component
@Primary
public class MainServiceInternalAuthApiClient implements InternalAuthApiClient {

  private final String checkEndpoint;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final ServiceTokenGenerator serviceTokenGenerator;
  private final String serviceName;
  private final String serviceJwtSecret;
  private final int serviceJwtExpirationSeconds;
  private final GatewayProperties.AuthVersionProperties authVersionProperties;

  public MainServiceInternalAuthApiClient(GatewayProperties gatewayProperties,
      SecurityProperties securityProperties,
      ServiceTokenGenerator serviceTokenGenerator,
      ObjectMapper objectMapper) {
    this.authVersionProperties = gatewayProperties.getAuthVersion();
    String mainServiceUrl = gatewayProperties.getRoutes().get("main-service").getUrl();
    this.checkEndpoint = mainServiceUrl + "/internal/v1/auth/version/check";
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(authVersionProperties.getRequestTimeoutMillis()))
        .build();
    this.serviceTokenGenerator = serviceTokenGenerator;
    this.objectMapper = objectMapper;
    this.serviceName = securityProperties.getServiceName();
    this.serviceJwtSecret = securityProperties.getJwt().getService().getSecret();
    this.serviceJwtExpirationSeconds = securityProperties.getJwt().getService().getExpiration();
  }

  @Override
  public AuthVersionCheckResult checkAuthVersion(Long userId, Integer authVersion) {
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
        throw new IllegalStateException("authVersion check failed with status=" + response.statusCode());
      }
      return objectMapper.readValue(response.body(), AuthVersionCheckResult.class);
    } catch (Exception ex) {
      throw new IllegalStateException("authVersion check request error", ex);
    }
  }

  private String generateServiceToken() {
    return serviceTokenGenerator.generate(serviceName, null, List.of(), serviceJwtSecret,
        serviceJwtExpirationSeconds);
  }
}
