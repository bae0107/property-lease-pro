package com.jugu.propertylease.security.filter.reactive;

import com.jugu.propertylease.security.autoconfigure.SecurityResponseUtils;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.exception.InvalidTokenException;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.JwtTokenParser;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import com.jugu.propertylease.security.token.UserTokenPayload;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Gateway 专用响应式过滤器：验证 User JWT，转换为携带用户上下文的 Service JWT 后转发。
 *
 * <ul>
 *   <li>放行路径：直接透传，不生成 Service JWT</li>
 *   <li>认证路径：验证 User JWT → 生成 Service JWT → 移除 Authorization Header → 添加 X-Service-Token</li>
 * </ul>
 *
 * <p>TraceId 处理：
 * <ul>
 *   <li>ensureTraceId() 在所有路径（放行/认证/拒绝）最先执行</li>
 *   <li>traceId 同时写入两处：request header（透传给下游）+ exchange attributes（供
 *       {@code GatewayErrorWebExceptionHandler} 在收到 originalExchange 时读取）</li>
 *   <li>exchange.mutate().request().build() 构建新 exchange 时 attributes map 指向同一实例，
 *       因此写入 originalExchange attributes 的 traceId 在 mutatedExchange 中同样可见</li>
 * </ul>
 *
 * <p>严格响应式，禁止阻塞调用（block()/get()）。
 * order = -100，在 Spring Security 过滤链之前执行。
 */
public class ReactiveUserJwtFilter implements WebFilter, Ordered {

  /**
   * exchange attributes key，供 GatewayErrorWebExceptionHandler 读取 traceId
   */
  public static final String ATTR_TRACE_ID = "traceId";

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenParser jwtTokenParser;
  private final ServiceTokenGenerator serviceTokenGenerator;
  private final SecurityProperties properties;
  private final UserTokenVersionChecker userTokenVersionChecker;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public ReactiveUserJwtFilter(JwtTokenParser jwtTokenParser,
      ServiceTokenGenerator serviceTokenGenerator,
      SecurityProperties properties) {
    this(jwtTokenParser, serviceTokenGenerator, properties, (userId, authVersion) -> true);
  }

  public ReactiveUserJwtFilter(JwtTokenParser jwtTokenParser,
      ServiceTokenGenerator serviceTokenGenerator,
      SecurityProperties properties,
      UserTokenVersionChecker userTokenVersionChecker) {
    this.jwtTokenParser = jwtTokenParser;
    this.serviceTokenGenerator = serviceTokenGenerator;
    this.properties = properties;
    this.userTokenVersionChecker = userTokenVersionChecker;
  }

  @Override
  public int getOrder() {
    return -100;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    // 1. 先处理 TraceId（所有路径均需透传/生成，与认证逻辑解耦）
    //    traceId 同时写入 request header 和 exchange attributes：
    //    - header：用于透传给下游微服务
    //    - attributes：用于 GatewayErrorWebExceptionHandler 在 originalExchange 中读取
    exchange = ensureTraceId(exchange);

    // 2. 放行路径判断
    if (isPermittedPath(path)) {
      return chain.filter(exchange);
    }

    // 3. 读取 Authorization Header
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || authHeader.isBlank()) {
      return write401(exchange, InvalidTokenException.TOKEN_MISSING,
          "Authorization header is required");
    }
    if (!authHeader.startsWith(BEARER_PREFIX)) {
      return write401(exchange, InvalidTokenException.TOKEN_MALFORMED,
          "Invalid token format, expected Bearer token");
    }

    String bearerToken = authHeader.substring(BEARER_PREFIX.length()).trim();
    String userSecret = properties.getJwt().getUser().getSecret();

    // 4. 解析 User JWT（用 fromCallable 包装同步 CPU 操作）
    ServerWebExchange finalExchange = exchange;
    return Mono.fromCallable(() -> jwtTokenParser.parseUserToken(bearerToken, userSecret))
        .flatMap(payload -> Mono.fromCallable(() -> {
          validateUserTokenVersion(payload);
          return payload;
        }).subscribeOn(Schedulers.boundedElastic()))
        .flatMap(payload -> chain.filter(buildForwardExchange(finalExchange, payload)))
        .onErrorResume(InvalidTokenException.class,
            e -> write401(finalExchange, e.getErrorCode(), e.getMessage()))
        .onErrorResume(Exception.class,
            e -> write401(finalExchange, InvalidTokenException.TOKEN_MALFORMED,
                "Token processing failed"));
  }

  // ===== Private helpers =====

  private boolean isPermittedPath(String path) {
    return properties.getEffectivePermitPaths().stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private void validateUserTokenVersion(UserTokenPayload payload) {
    if (payload.authVersion() == null || payload.authVersion() < 0) {
      throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID,
          "User token authVersion claim is required");
    }
    if (!userTokenVersionChecker.isCurrent(payload.userId(), payload.authVersion())) {
      throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID,
          "User token authVersion is stale");
    }
  }

  /**
   * 构建转发 Exchange：移除原始 Authorization Header，添加 X-Service-Token。
   */
  private ServerWebExchange buildForwardExchange(ServerWebExchange exchange,
      UserTokenPayload payload) {
    String serviceToken = serviceTokenGenerator.generate(
        properties.getServiceName(),
        payload.userId(),
        payload.permissions(),
        properties.getJwt().getService().getSecret(),
        properties.getJwt().getService().getExpiration());

    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
        .headers(headers -> {
          headers.remove(HttpHeaders.AUTHORIZATION); // 移除 User JWT，阻止进入内网
          headers.set(SecurityConstants.HEADER_SERVICE_TOKEN, serviceToken);
        })
        .build();

    return exchange.mutate().request(mutatedRequest).build();
  }

  /**
   * 确保 X-Trace-Id 存在：已有则透传，无则生成。
   *
   * <p>同时写入两处：
   * <ol>
   *   <li>request header — 透传给下游微服务，下游 TraceIdFilter 读取后写入 MDC</li>
   *   <li>exchange attributes — 供 {@code GatewayErrorWebExceptionHandler} 读取；
   *       ErrorWebExceptionHandler 收到的是 originalExchange，无法访问
   *       mutatedRequest 的 header，但 attributes 在 mutate() 中共享同一实例</li>
   * </ol>
   */
  private ServerWebExchange ensureTraceId(ServerWebExchange exchange) {
    String traceId = exchange.getRequest().getHeaders()
        .getFirst(SecurityConstants.HEADER_TRACE_ID);

    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString().replace("-", "");
      // 写入 request header（新建 mutated request）
      ServerHttpRequest mutated = exchange.getRequest().mutate()
          .header(SecurityConstants.HEADER_TRACE_ID, traceId)
          .build();
      exchange = exchange.mutate().request(mutated).build();
    }

    // 无论是新生成还是已有，都写入 attributes，保证 ErrorWebExceptionHandler 可读
    exchange.getAttributes().put(ATTR_TRACE_ID, traceId);
    return exchange;
  }

  private Mono<Void> write401(ServerWebExchange exchange, String code, String message) {
    // 优先从 attributes 读（ensureTraceId 已写入），兜底从 header 读
    String traceId = exchange.getAttribute(ATTR_TRACE_ID);
    if (traceId == null) {
      traceId = exchange.getRequest().getHeaders()
          .getFirst(SecurityConstants.HEADER_TRACE_ID);
    }
    String body = SecurityResponseUtils.buildErrorJson(code, message, traceId);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
