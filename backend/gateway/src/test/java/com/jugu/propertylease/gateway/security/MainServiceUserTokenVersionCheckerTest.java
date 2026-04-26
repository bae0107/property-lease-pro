package com.jugu.propertylease.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.gateway.config.GatewayProperties;
import com.jugu.propertylease.main.client.internal.api.InternalAuthApiClient;
import com.jugu.propertylease.main.client.internal.model.AuthVersionCheckResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MainServiceUserTokenVersionCheckerTest {

  @Test
  void isCurrent_shouldReadMainServiceResponseAndUseCache() throws Exception {
    InternalAuthApiClient internalAuthApiClient = Mockito.mock(InternalAuthApiClient.class);
    when(internalAuthApiClient.checkAuthVersion(1L, 3))
        .thenReturn(new AuthVersionCheckResult().current(true));

    MainServiceUserTokenVersionChecker checker = new MainServiceUserTokenVersionChecker(
        buildGatewayProperties("http://localhost:8080"), internalAuthApiClient);

    assertThat(checker.isCurrent(1L, 3)).isTrue();
    assertThat(checker.isCurrent(1L, 3)).isTrue();
    verify(internalAuthApiClient, times(1)).checkAuthVersion(1L, 3);
  }

  @Test
  void isCurrent_shouldRejectWhenMainServiceReturnsFalse() throws Exception {
    InternalAuthApiClient internalAuthApiClient = Mockito.mock(InternalAuthApiClient.class);
    when(internalAuthApiClient.checkAuthVersion(1L, 3))
        .thenReturn(new AuthVersionCheckResult().current(false));

    MainServiceUserTokenVersionChecker checker = new MainServiceUserTokenVersionChecker(
        buildGatewayProperties("http://localhost:8080"), internalAuthApiClient);

    assertThat(checker.isCurrent(1L, 3)).isFalse();
  }

  @Test
  void isCurrent_shouldFollowFailOpenWhenApiThrows() {
    InternalAuthApiClient internalAuthApiClient = Mockito.mock(InternalAuthApiClient.class);
    when(internalAuthApiClient.checkAuthVersion(any(), any())).thenThrow(new RuntimeException("timeout"));

    GatewayProperties gatewayProperties = buildGatewayProperties("http://localhost:8080");
    gatewayProperties.getAuthVersion().setFailOpen(true);
    MainServiceUserTokenVersionChecker checker = new MainServiceUserTokenVersionChecker(
        gatewayProperties, internalAuthApiClient);

    assertThat(checker.isCurrent(1L, 3)).isTrue();
  }

  private GatewayProperties buildGatewayProperties(String mainServiceUrl) {
    GatewayProperties properties = new GatewayProperties();
    GatewayProperties.RouteProperties mainRoute = new GatewayProperties.RouteProperties();
    mainRoute.setUrl(mainServiceUrl);
    GatewayProperties.RouteProperties billingRoute = new GatewayProperties.RouteProperties();
    billingRoute.setUrl("http://billing-service");
    GatewayProperties.RouteProperties deviceRoute = new GatewayProperties.RouteProperties();
    deviceRoute.setUrl("http://device-service");
    properties.setRoutes(Map.of(
        "main-service", mainRoute,
        "billing-service", billingRoute,
        "device-service", deviceRoute));

    GatewayProperties.AuthVersionProperties authVersion = new GatewayProperties.AuthVersionProperties();
    authVersion.setEnabled(true);
    authVersion.setCacheTtlSeconds(60);
    authVersion.setRequestTimeoutMillis(1000);
    authVersion.setFailOpen(false);
    properties.setAuthVersion(authVersion);
    return properties;
  }
}
