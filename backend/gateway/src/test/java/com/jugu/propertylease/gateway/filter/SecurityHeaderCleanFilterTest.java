package com.jugu.propertylease.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.gateway.config.GatewayProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("SecurityHeaderCleanFilter 测试")
class SecurityHeaderCleanFilterTest {

  private GatewayProperties properties;
  private GatewayFilterChain chain;

  @BeforeEach
  void setUp() {
    properties = new GatewayProperties();
    chain = mock(GatewayFilterChain.class);
    when(chain.filter(any())).thenReturn(Mono.empty());
  }

  /**
   * 捕获传递给 chain 的 exchange，使用 ServerWebExchange（父类型）接收。 filter 内部调用 exchange.mutate().build() 返回的是
   * MutativeDecorator， 不是 MockServerWebExchange，因此不能强转为 MockServerWebExchange。
   */
  private AtomicReference<ServerWebExchange> capturingChain(
      SecurityHeaderCleanFilter filter,
      MockServerWebExchange exchange) {

    AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
    GatewayFilterChain capturingChain = ex -> {
      captured.set(ex);          // 用 ServerWebExchange 接收，不强转
      return Mono.empty();
    };
    StepVerifier.create(filter.filter(exchange, capturingChain)).verifyComplete();
    return captured;
  }

  @Test
  @DisplayName("Order 应为 -200")
  void order_shouldBeMinus200() {
    SecurityHeaderCleanFilter filter = new SecurityHeaderCleanFilter(properties);
    assertThat(filter.getOrder()).isEqualTo(-200);
  }

  @Test
  @DisplayName("外部传入 X-Service-Token 应被移除")
  void filter_shouldRemoveXServiceToken() {
    MockServerHttpRequest request = MockServerHttpRequest
        .get("/api/main-service/test")
        .header("X-Service-Token", "fake-token")
        .header("Authorization", "Bearer real-user-token")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    SecurityHeaderCleanFilter filter = new SecurityHeaderCleanFilter(properties);
    AtomicReference<ServerWebExchange> captured = capturingChain(filter, exchange);

    HttpHeaders headers = captured.get().getRequest().getHeaders();
    assertThat(headers.get("X-Service-Token")).isNull();
    // Authorization Header 不受影响
    assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer real-user-token");
  }

  @Test
  @DisplayName("请求中不存在配置的 Header 时静默跳过")
  void filter_shouldSilentlySkipNonExistentHeader() {
    MockServerHttpRequest request = MockServerHttpRequest
        .get("/api/main-service/test")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    SecurityHeaderCleanFilter filter = new SecurityHeaderCleanFilter(properties);

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
  }

  @Test
  @DisplayName("配置多个清洗 Header 时全部移除")
  void filter_shouldRemoveAllConfiguredHeaders() {
    GatewayProperties.SecurityHeaderProperties securityProps =
        new GatewayProperties.SecurityHeaderProperties();
    securityProps.setStripRequestHeaders(List.of("X-Service-Token", "X-Internal-Debug"));
    properties.setSecurity(securityProps);

    MockServerHttpRequest request = MockServerHttpRequest
        .get("/api/test")
        .header("X-Service-Token", "fake-token")
        .header("X-Internal-Debug", "true")
        .header("X-Keep-This", "value")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    SecurityHeaderCleanFilter filter = new SecurityHeaderCleanFilter(properties);
    AtomicReference<ServerWebExchange> captured = capturingChain(filter, exchange);

    HttpHeaders headers = captured.get().getRequest().getHeaders();
    assertThat(headers.get("X-Service-Token")).isNull();
    assertThat(headers.get("X-Internal-Debug")).isNull();
    assertThat(headers.getFirst("X-Keep-This")).isEqualTo("value");
  }

  @Test
  @DisplayName("清洗列表为空时请求 Header 保持不变")
  void filter_shouldPassThroughWhenNothingToStrip() {
    GatewayProperties.SecurityHeaderProperties securityProps =
        new GatewayProperties.SecurityHeaderProperties();
    securityProps.setStripRequestHeaders(List.of());
    properties.setSecurity(securityProps);

    MockServerHttpRequest request = MockServerHttpRequest
        .get("/api/test")
        .header("X-Service-Token", "should-remain")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    SecurityHeaderCleanFilter filter = new SecurityHeaderCleanFilter(properties);
    AtomicReference<ServerWebExchange> captured = capturingChain(filter, exchange);

    assertThat(captured.get().getRequest().getHeaders().getFirst("X-Service-Token"))
        .isEqualTo("should-remain");
  }
}
