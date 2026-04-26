package com.jugu.propertylease.security.filter.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jugu.propertylease.security.authentication.ServiceJwtAuthenticationToken;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.exception.InvalidTokenException;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ServletServiceJwtFilterTest {

  private static final String SECRET = "service-secret-key-at-least-32-bytes!";
  @Mock
  FilterChain chain;
  private JwtTokenParser parser;
  private ServiceTokenGenerator generator;
  private SecurityProperties props;
  private ServletServiceJwtFilter filter;

  @BeforeEach
  void setUp() {
    parser = new JwtTokenParser();
    generator = new ServiceTokenGenerator();

    props = new SecurityProperties();
    props.setMode("service");
    props.setServiceName("billing-service");
    SecurityProperties.JwtConfig jwt = new SecurityProperties.JwtConfig();
    com.jugu.propertylease.security.properties.JwtProperties svc =
        new com.jugu.propertylease.security.properties.JwtProperties();
    svc.setSecret(SECRET);
    svc.setExpiration(300);
    jwt.setService(svc);
    props.setJwt(jwt);

    filter = new ServletServiceJwtFilter(parser, props);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  // ===== shouldNotFilter：permit paths 在 Filter 层跳过 =====

  @Test
  void permitPath_noToken_filterIsSkipped() throws Exception {
    // 配置 /auth/login 为放行路径
    props.setPermitPaths(List.of("/auth/login"));
    filter = new ServletServiceJwtFilter(parser, props);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
    request.setServletPath("/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    // shouldNotFilter=true → doFilterInternal 不执行 → chain 正常调用 → 无 401
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void permitPath_withWildcard_filterIsSkipped() throws Exception {
    props.setPermitPaths(List.of("/public/**"));
    filter = new ServletServiceJwtFilter(parser, props);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/health");
    request.setServletPath("/public/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void nonPermitPath_noToken_filterBlocks() throws Exception {
    props.setPermitPaths(List.of("/auth/login"));
    filter = new ServletServiceJwtFilter(parser, props);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
    request.setServletPath("/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    // shouldNotFilter=false → doFilterInternal 执行 → token 缺失 → 403（当前实现）
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains(InvalidTokenException.TOKEN_MISSING);
    verifyNoInteractions(chain);
  }

  @Test
  void actuatorPath_alwaysSkipped_viaDefaultPermitPaths() throws Exception {
    // /actuator/** 由 SecurityConstants.DEFAULT_PERMIT_PATHS 内置，无需配置
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    request.setServletPath("/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  // ===== 正常路径 =====

  @Test
  void validUserToken_writesAuthenticationAndChainsFilter() throws Exception {
    String token = generator.generate("gateway", 42L, List.of("order:read"), SECRET, 300);
    MockHttpServletRequest request = buildRequest(token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isInstanceOf(ServiceJwtAuthenticationToken.class);
    ServiceJwtAuthenticationToken sja = (ServiceJwtAuthenticationToken) auth;
    assertThat(sja.getUserId()).isEqualTo(42L);
    assertThat(sja.getCallerName()).isEqualTo("gateway");
  }

  @Test
  void systemToken_writesSystemAuthentication() throws Exception {
    String token = generator.generate("main-service", null, List.of(), SECRET, 300);
    MockHttpServletRequest request = buildRequest(token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    ServiceJwtAuthenticationToken auth =
        (ServiceJwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth.getUserId()).isNull();
    assertThat(auth.getCallerName()).isEqualTo("main-service");
  }

  @Test
  void validToken_withMultiplePermissions_authoritiesAreSet() throws Exception {
    String token = generator.generate("gateway", 1L,
        List.of("order:read", "order:write", "device:command"), SECRET, 300);
    MockHttpServletRequest request = buildRequest(token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    ServiceJwtAuthenticationToken auth =
        (ServiceJwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    var authorities = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
    assertThat(authorities).containsExactlyInAnyOrder("order:read", "order:write",
        "device:command");
  }

  // ===== Token 缺失 / 格式错误：直接写 403，不再抛异常 =====

  @Test
  void missingToken_writes403WithTokenMissingCode() throws Exception {
    MockHttpServletRequest request = buildRequest(null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains(InvalidTokenException.TOKEN_MISSING);
    verifyNoInteractions(chain);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void blankToken_writes403WithTokenMissingCode() throws Exception {
    MockHttpServletRequest request = buildRequest("   ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains(InvalidTokenException.TOKEN_MISSING);
    verifyNoInteractions(chain);
  }

  @Test
  void invalidToken_writes403WithErrorCode() throws Exception {
    MockHttpServletRequest request = buildRequest("bad.token.here");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("IAM_TOKEN_");
    verifyNoInteractions(chain);
  }

  // ===== traceId 透传 =====

  @Test
  void missingToken_responseContainsTraceIdFromMdc() throws Exception {
    MDC.put("traceId", "test-trace-999");
    MockHttpServletRequest request = buildRequest(null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertThat(response.getContentAsString()).contains("test-trace-999");
  }

  // ===== 工具方法 =====

  private MockHttpServletRequest buildRequest(String token) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    if (token != null) {
      req.addHeader(SecurityConstants.HEADER_SERVICE_TOKEN, token);
    }
    return req;
  }
}
