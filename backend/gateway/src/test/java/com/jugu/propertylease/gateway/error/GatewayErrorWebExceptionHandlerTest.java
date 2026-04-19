package com.jugu.propertylease.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.security.filter.reactive.ReactiveUserJwtFilter;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

@DisplayName("GatewayErrorWebExceptionHandler 测试")
class GatewayErrorWebExceptionHandlerTest {

  private GatewayErrorWebExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GatewayErrorWebExceptionHandler();
  }

  private MockServerWebExchange exchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/test").build());
  }

  // ===== 异常分类映射（原有）=====

  @Test
  @DisplayName("ResponseStatusException(404) 映射为 GATEWAY_ROUTE_NOT_FOUND")
  void handle_404ResponseStatusException_shouldReturnRouteNotFound() {
    MockServerWebExchange ex = exchange();
    org.springframework.web.server.ResponseStatusException notFound =
        new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);

    StepVerifier.create(handler.handle(ex, notFound)).verifyComplete();

    assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    String body = getBody(ex);
    assertThat(body).contains("GATEWAY_ROUTE_NOT_FOUND");
    assertThat(body).contains("\"code\"");
  }

  @Test
  @DisplayName("ConnectException 映射为 GATEWAY_DOWNSTREAM_UNAVAILABLE (502)")
  void handle_connectException_shouldReturn502() {
    MockServerWebExchange ex = exchange();
    StepVerifier.create(handler.handle(ex, new ConnectException("Connection refused")))
        .verifyComplete();

    assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(getBody(ex)).contains("GATEWAY_DOWNSTREAM_UNAVAILABLE");
  }

  @Test
  @DisplayName("ConnectException 包装在 RuntimeException 中也能识别")
  void handle_wrappedConnectException_shouldReturn502() {
    MockServerWebExchange ex = exchange();
    RuntimeException wrapped = new RuntimeException("wrapped", new ConnectException("refused"));

    StepVerifier.create(handler.handle(ex, wrapped)).verifyComplete();

    assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(getBody(ex)).contains("GATEWAY_DOWNSTREAM_UNAVAILABLE");
  }

  @Test
  @DisplayName("TimeoutException 映射为 GATEWAY_DOWNSTREAM_TIMEOUT (504)")
  void handle_timeoutException_shouldReturn504() {
    MockServerWebExchange ex = exchange();
    StepVerifier.create(handler.handle(ex, new TimeoutException("timeout")))
        .verifyComplete();

    assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    assertThat(getBody(ex)).contains("GATEWAY_DOWNSTREAM_TIMEOUT");
  }

  @Test
  @DisplayName("未知异常兜底返回 GATEWAY_DOWNSTREAM_UNAVAILABLE (502)")
  void handle_unknownException_shouldReturn502Fallback() {
    MockServerWebExchange ex = exchange();
    StepVerifier.create(handler.handle(ex, new RuntimeException("unexpected")))
        .verifyComplete();

    assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(getBody(ex)).contains("GATEWAY_DOWNSTREAM_UNAVAILABLE");
  }

  @Test
  @DisplayName("响应 Content-Type 为 application/json")
  void handle_shouldSetJsonContentType() {
    MockServerWebExchange ex = exchange();
    StepVerifier.create(handler.handle(ex, new ConnectException("refused")))
        .verifyComplete();

    assertThat(ex.getResponse().getHeaders().getContentType())
        .hasToString("application/json");
  }

  // ===== TraceId 携带（Bug3修复）=====

  @Test
  @DisplayName("Client 携带 X-Trace-Id 时，错误响应体包含 traceId")
  void handle_clientHasTraceIdInHeader_traceIdInBody() {
    // Client 请求本身带了 X-Trace-Id，originalExchange header 有值，兜底路径可读到
    MockServerWebExchange ex = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/test")
            .header("X-Trace-Id", "client-trace-001")
            .build());

    StepVerifier.create(handler.handle(ex, new ConnectException("refused")))
        .verifyComplete();

    assertThat(getBody(ex)).contains("client-trace-001");
  }

  @Test
  @DisplayName("ReactiveUserJwtFilter 生成的 traceId 写入 attributes 后，错误响应体包含 traceId")
  void handle_traceIdFromExchangeAttributes_traceIdInBody() {
    // 模拟 ReactiveUserJwtFilter 的行为：Client 未携带 X-Trace-Id，
    // ReactiveUserJwtFilter 生成新 traceId 并写入 exchange.getAttributes()
    MockServerWebExchange ex = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/test").build()); // 无 X-Trace-Id header

    // 模拟 ReactiveUserJwtFilter.ensureTraceId() 写入 attributes 的行为
    ex.getAttributes().put(ReactiveUserJwtFilter.ATTR_TRACE_ID, "generated-trace-999");

    StepVerifier.create(handler.handle(ex, new ConnectException("refused")))
        .verifyComplete();

    // 应从 attributes 读到 traceId，而非 header（此场景 header 无值）
    assertThat(getBody(ex)).contains("generated-trace-999");
  }

  @Test
  @DisplayName("attributes 优先于 header：两者都有时，attributes 的值优先")
  void handle_attributesTakesPriorityOverHeader() {
    MockServerWebExchange ex = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/test")
            .header("X-Trace-Id", "header-trace")
            .build());

    // attributes 写入不同值（模拟 ReactiveUserJwtFilter 已写入）
    ex.getAttributes().put(ReactiveUserJwtFilter.ATTR_TRACE_ID, "attribute-trace");

    StepVerifier.create(handler.handle(ex, new ConnectException("refused")))
        .verifyComplete();

    assertThat(getBody(ex)).contains("attribute-trace");
    assertThat(getBody(ex)).doesNotContain("header-trace");
  }

  @Test
  @DisplayName("Client 无 traceId 且无 attributes 时，traceId 为 null（不在响应体中）")
  void handle_noTraceIdAnywhere_traceIdAbsentInBody() {
    MockServerWebExchange ex = exchange(); // 无 header，无 attributes

    StepVerifier.create(handler.handle(ex, new ConnectException("refused")))
        .verifyComplete();

    // traceId 为 null 时，@JsonInclude(NON_NULL) 不序列化，响应体无 traceId 字段
    assertThat(getBody(ex)).doesNotContain("traceId");
  }

  private String getBody(MockServerWebExchange exchange) {
    return exchange.getResponse().getBodyAsString().block();
  }
}
