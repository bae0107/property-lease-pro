package com.jugu.propertylease.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.gateway.config.GatewayProperties;
import com.jugu.propertylease.security.properties.JwtProperties;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainServiceUserTokenVersionCheckerTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void isCurrent_shouldReadMainServiceResponseAndUseCache() throws Exception {
    AtomicInteger calls = new AtomicInteger(0);
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/internal/v1/auth/version/check", exchange -> {
      calls.incrementAndGet();
      byte[] body = "{\"current\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream outputStream = exchange.getResponseBody()) {
        outputStream.write(body);
      }
    });
    server.start();

    MainServiceUserTokenVersionChecker checker = new MainServiceUserTokenVersionChecker(
        buildGatewayProperties("http://localhost:" + server.getAddress().getPort()),
        buildSecurityProperties(),
        new ServiceTokenGenerator(),
        new ObjectMapper());

    assertThat(checker.isCurrent(1L, 3)).isTrue();
    assertThat(checker.isCurrent(1L, 3)).isTrue();
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void isCurrent_shouldRejectWhenMainServiceReturnsFalse() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/internal/v1/auth/version/check", exchange -> respond(exchange, 200,
        "{\"current\":false}"));
    server.start();

    MainServiceUserTokenVersionChecker checker = new MainServiceUserTokenVersionChecker(
        buildGatewayProperties("http://localhost:" + server.getAddress().getPort()),
        buildSecurityProperties(),
        new ServiceTokenGenerator(),
        new ObjectMapper());

    assertThat(checker.isCurrent(1L, 3)).isFalse();
  }

  private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
      throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream outputStream = exchange.getResponseBody()) {
      outputStream.write(body);
    }
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

  private SecurityProperties buildSecurityProperties() {
    SecurityProperties properties = new SecurityProperties();
    properties.setServiceName("gateway");

    SecurityProperties.JwtConfig jwtConfig = new SecurityProperties.JwtConfig();
    JwtProperties serviceJwt = new JwtProperties();
    serviceJwt.setSecret("service-secret-at-least-32-bytes!!!");
    serviceJwt.setExpiration(300);
    jwtConfig.setService(serviceJwt);

    JwtProperties userJwt = new JwtProperties();
    userJwt.setSecret("user-secret-key-at-least-32-bytes!!");
    userJwt.setExpiration(1800);
    jwtConfig.setUser(userJwt);

    properties.setJwt(jwtConfig);
    return properties;
  }
}
