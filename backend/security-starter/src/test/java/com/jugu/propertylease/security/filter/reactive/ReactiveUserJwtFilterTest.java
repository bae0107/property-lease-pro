package com.jugu.propertylease.security.filter.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.exception.InvalidTokenException;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactiveUserJwtFilterTest {

  private static final String USER_SECRET = "user-secret-key-at-least-32-bytes!!";
  private static final String SERVICE_SECRET = "service-secret-at-least-32-bytes!!!";

  private JwtTokenParser parser;
  private ServiceTokenGenerator generator;
  private SecurityProperties props;
  private ReactiveUserJwtFilter filter;

  @BeforeEach
  void setUp() {
    parser = new JwtTokenParser();
    generator = new ServiceTokenGenerator();

    props = new SecurityProperties();
    props.setMode("gateway");
    props.setServiceName("gateway");

    SecurityProperties.JwtConfig jwt = new SecurityProperties.JwtConfig();

    var user = new com.jugu.propertylease.security.properties.JwtProperties();
    user.setSecret(USER_SECRET);
    user.setExpiration(1800);
    jwt.setUser(user);

    var svc = new com.jugu.propertylease.security.properties.JwtProperties();
    svc.setSecret(SERVICE_SECRET);
    svc.setExpiration(300);
    jwt.setService(svc);

    props.setJwt(jwt);

    filter = new ReactiveUserJwtFilter(parser, generator, props);
  }

  // ─── permitted paths ───

  @Test
  void actuatorPath_isPassedThrough_withoutAuth() {
    var exchange = buildExchange("/actuator/health", null);
    var chain = chainThatSucceeds();

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    // chain must have been called — response should not be 401
    assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void customPermitPath_isPassedThrough() {
    props.setPermitPaths(List.of("/api/v1/auth/login"));
    var exchange = buildExchange("/api/v1/auth/login", null);
    var chain = chainThatSucceeds();

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();
  }

  // ─── authentication failures ───

  @Test
  void missingAuthHeader_returns401() {
    var exchange = buildExchange("/api/v1/orders", null);
    var chain = chainThatShouldNotBeCalled();

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(bodyAsString(exchange)).contains(InvalidTokenException.TOKEN_MISSING);
  }

  @Test
  void malformedAuthHeader_notBearer_returns401() {
    var exchange = buildExchange("/api/v1/orders", "Basic some-creds");
    var chain = chainThatShouldNotBeCalled();

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(bodyAsString(exchange)).contains(InvalidTokenException.TOKEN_MALFORMED);
  }

  @Test
  void expiredUserToken_returns401WithExpiredCode() {
    SecretKey key = Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8));
    String expired = Jwts.builder()
        .subject("user@example.com")
        .claim("userId", 1L)
        .issuedAt(new Date(System.currentTimeMillis() - 10_000))
        .expiration(new Date(System.currentTimeMillis() - 5_000))
        .signWith(key)
        .compact();

    var exchange = buildExchange("/api/v1/orders", "Bearer " + expired);
    var chain = chainThatShouldNotBeCalled();

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(bodyAsString(exchange)).contains(InvalidTokenException.TOKEN_EXPIRED);
  }

  @Test
  void staleAuthVersion_returns401() {
    SecretKey key = Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8));
    String validToken = Jwts.builder()
        .subject("user@example.com")
        .claim("userId", 99L)
        .claim("authVersion", 1)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 300_000))
        .signWith(key)
        .compact();

    ReactiveUserJwtFilter staleRejectFilter = new ReactiveUserJwtFilter(parser, generator, props,
        (userId, authVersion) -> false);

    var exchange = buildExchange("/api/v1/orders", "Bearer " + validToken);
    var chain = chainThatShouldNotBeCalled();
    StepVerifier.create(staleRejectFilter.filter(exchange, chain))
        .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(bodyAsString(exchange)).contains(InvalidTokenException.TOKEN_INVALID)
        .contains("stale");
  }

  // ─── authentication success ───

  @Test
  void validUserToken_removesAuthorizationAndAddsServiceToken() {
    SecretKey key = Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8));
    String validToken = Jwts.builder()
        .subject("user@example.com")
        .claim("userId", 99L)
        .claim("permissions", List.of("order:read"))
        .claim("authVersion", 1)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 300_000))
        .signWith(key)
        .compact();

    // Capture the mutated request inside the chain
    String[] capturedServiceToken = new String[1];
    String[] capturedAuthHeader = new String[1];
    WebFilterChain capturingChain = ex -> {
      capturedServiceToken[0] = ex.getRequest().getHeaders()
          .getFirst(SecurityConstants.HEADER_SERVICE_TOKEN);
      capturedAuthHeader[0] = ex.getRequest().getHeaders()
          .getFirst(HttpHeaders.AUTHORIZATION);
      return Mono.empty();
    };

    var exchange = buildExchange("/api/v1/orders", "Bearer " + validToken);

    StepVerifier.create(filter.filter(exchange, capturingChain))
        .verifyComplete();

    // Authorization header must be removed
    assertThat(capturedAuthHeader[0]).isNull();
    // Service token must be present
    assertThat(capturedServiceToken[0]).isNotBlank();
    // Service token must be parseable
    var payload = parser.parseServiceToken(capturedServiceToken[0], SERVICE_SECRET);
    assertThat(payload.userId()).isEqualTo(99L);
    assertThat(payload.permissions()).containsExactly("order:read");
    assertThat(payload.serviceName()).isEqualTo("gateway");
  }

  @Test
  void traceId_generatedWhenAbsent() {
    SecretKey key = Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8));
    String valid = Jwts.builder()
        .subject("u")
        .claim("userId", 1L)
        .claim("authVersion", 1)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 300_000))
        .signWith(key).compact();

    String[] capturedTrace = new String[1];
    WebFilterChain capturingChain = ex -> {
      capturedTrace[0] = ex.getRequest().getHeaders()
          .getFirst(SecurityConstants.HEADER_TRACE_ID);
      return Mono.empty();
    };

    var exchange = buildExchange("/api/v1/orders", "Bearer " + valid);
    StepVerifier.create(filter.filter(exchange, capturingChain)).verifyComplete();

    assertThat(capturedTrace[0]).isNotBlank();
  }

  @Test
  void traceId_preservedWhenPresent() {
    SecretKey key = Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8));
    String valid = Jwts.builder()
        .subject("u").claim("userId", 1L).claim("authVersion", 1)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 300_000))
        .signWith(key).compact();

    String existingTraceId = "existing-trace-001";
    String[] capturedTrace = new String[1];
    WebFilterChain capturingChain = ex -> {
      capturedTrace[0] = ex.getRequest().getHeaders()
          .getFirst(SecurityConstants.HEADER_TRACE_ID);
      return Mono.empty();
    };

    MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/orders")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + valid)
        .header(SecurityConstants.HEADER_TRACE_ID, existingTraceId)
        .build();
    var exchange = MockServerWebExchange.from(req);

    StepVerifier.create(filter.filter(exchange, capturingChain)).verifyComplete();

    assertThat(capturedTrace[0]).isEqualTo(existingTraceId);
  }

  @Test
  void order_isMinusHundred() {
    assertThat(filter.getOrder()).isEqualTo(-100);
  }

  // ─── helpers ───

  private MockServerWebExchange buildExchange(String path, String authHeader) {
    MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
    if (authHeader != null) {
      builder.header(HttpHeaders.AUTHORIZATION, authHeader);
    }
    return MockServerWebExchange.from((MockServerHttpRequest) builder.build());
  }

  private WebFilterChain chainThatSucceeds() {
    return exchange -> Mono.empty();
  }

  private WebFilterChain chainThatShouldNotBeCalled() {
    return exchange -> {
      throw new AssertionError("chain should not be called");
    };
  }

  private String bodyAsString(MockServerWebExchange exchange) {
    return exchange.getResponse().getBodyAsString().block();
  }
}
