package com.jugu.propertylease.security.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceTokenClientInterceptorTest {

  private static final String SECRET = "service-secret-key-at-least-32-bytes!";

  private ServiceTokenClientInterceptor interceptor;
  private JwtTokenParser parser;

  @BeforeEach
  void setUp() {
    SecurityProperties props = new SecurityProperties();
    props.setMode("service");
    props.setServiceName("main-service");
    SecurityProperties.JwtConfig jwt = new SecurityProperties.JwtConfig();
    var svc = new com.jugu.propertylease.security.properties.JwtProperties();
    svc.setSecret(SECRET);
    svc.setExpiration(300);
    jwt.setService(svc);
    props.setJwt(jwt);

    ServiceTokenGenerator generator = new ServiceTokenGenerator();
    interceptor = new ServiceTokenClientInterceptor(props, generator);
    parser = new JwtTokenParser();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  // ===== Service Token 测试（原有） =====

  @Test
  void withAuthenticatedUser_addsServiceTokenHeader() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        ServiceJwtAuthenticationToken.ofUser(42L, "gateway",
            List.of("order:read", "billing:read")));

    var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://billing/test"));
    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(null);

    interceptor.intercept(request, new byte[0], execution);

    String tokenHeader = request.getHeaders().getFirst(SecurityConstants.HEADER_SERVICE_TOKEN);
    assertThat(tokenHeader).isNotBlank();

    var payload = parser.parseServiceToken(tokenHeader, SECRET);
    assertThat(payload.userId()).isEqualTo(42L);
    assertThat(payload.serviceName()).isEqualTo("main-service");
    assertThat(payload.permissions()).containsExactlyInAnyOrder("order:read", "billing:read");
  }

  @Test
  void withSystemContext_addsServiceTokenWithoutUserId() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        ServiceJwtAuthenticationToken.ofSystem("billing-service"));

    var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://device/test"));
    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(null);

    interceptor.intercept(request, new byte[0], execution);

    String tokenHeader = request.getHeaders().getFirst(SecurityConstants.HEADER_SERVICE_TOKEN);
    var payload = parser.parseServiceToken(tokenHeader, SECRET);
    assertThat(payload.userId()).isNull();
    assertThat(payload.permissions()).isEmpty();
  }

  @Test
  void unauthenticated_addsSystemToken() throws Exception {
    var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://billing/test"));
    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(null);

    interceptor.intercept(request, new byte[0], execution);

    String tokenHeader = request.getHeaders().getFirst(SecurityConstants.HEADER_SERVICE_TOKEN);
    assertThat(tokenHeader).isNotBlank();
    var payload = parser.parseServiceToken(tokenHeader, SECRET);
    assertThat(payload.userId()).isNull();
  }

  // ===== TraceId 透传测试（Bug1修复） =====

  @Test
  void whenMdcHasTraceId_addsTraceIdHeader() throws Exception {
    MDC.put("traceId", "abc123");

    var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://billing/test"));
    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(null);

    interceptor.intercept(request, new byte[0], execution);

    assertThat(request.getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID))
        .isEqualTo("abc123");
  }

  @Test
  void whenMdcHasNoTraceId_doesNotAddTraceIdHeader() throws Exception {
    // MDC 中没有 traceId（极端场景，TraceIdFilter 未运行）
    var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://billing/test"));
    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(null);

    interceptor.intercept(request, new byte[0], execution);

    assertThat(request.getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID)).isNull();
  }

  @Test
  void traceIdAndServiceToken_bothAttachedInSameRequest() throws Exception {
    MDC.put("traceId", "trace-xyz");
    SecurityContextHolder.getContext().setAuthentication(
        ServiceJwtAuthenticationToken.ofUser(1L, "gateway", List.of()));

    var request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://billing/api"));
    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(null);

    interceptor.intercept(request, new byte[0], execution);

    // 同一次出站请求，TraceId 和 ServiceToken 都应附加
    assertThat(request.getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID))
        .isEqualTo("trace-xyz");
    assertThat(request.getHeaders().getFirst(SecurityConstants.HEADER_SERVICE_TOKEN))
        .isNotBlank();
  }

  @Test
  void mockMode_noJwtConfig_onlyTraceIdIsAttached() throws Exception {
    // mock 模式：jwt.service 为 null，只透传 traceId，不附加 Service Token
    SecurityProperties mockProps = new SecurityProperties();
    mockProps.setMode("mock");
    mockProps.setServiceName("billing-service");
    // jwt 保持默认（service 为 null）
    ServiceTokenClientInterceptor mockInterceptor =
        new ServiceTokenClientInterceptor(mockProps, new ServiceTokenGenerator());

    MDC.put("traceId", "mock-trace");

    var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://other/api"));
    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(null);

    mockInterceptor.intercept(request, new byte[0], execution);

    assertThat(request.getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID))
        .isEqualTo("mock-trace");
    assertThat(request.getHeaders().getFirst(SecurityConstants.HEADER_SERVICE_TOKEN))
        .isNull();
  }
}
