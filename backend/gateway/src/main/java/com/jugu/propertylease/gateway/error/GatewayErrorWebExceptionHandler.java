package com.jugu.propertylease.gateway.error;

import com.jugu.propertylease.security.autoconfigure.SecurityResponseUtils;
import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.filter.reactive.ReactiveUserJwtFilter;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 层统一错误响应处理器（GW-3）。
 *
 * <p>优先级高于 Spring Boot 默认的 DefaultErrorWebExceptionHandler（Order=-1），
 * 通过 @Order(-2) 确保先被调用。
 *
 * <p>职责：仅处理 Gateway 自身抛出的异常，将其转换为统一 JSON 格式响应。
 * 下游微服务正常返回的 4xx/5xx 响应体直接透传，不经过此处理器（GW-C-07）。
 *
 * <p>响应格式复用 {@link SecurityResponseUtils#buildErrorJson}，与 security-starter 格式绝对一致：
 * <pre>
 * {
 *   "success": false,
 *   "error": {
 *     "code": "GATEWAY_ROUTE_NOT_FOUND",
 *     "message": "请求的路径不存在",
 *     "traceId": "abc123"
 *   }
 * }
 * </pre>
 */
@Order(-2)
@Component
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

  // Gateway 层错误码（GW-3.3）
  private static final String CODE_ROUTE_NOT_FOUND = "GATEWAY_ROUTE_NOT_FOUND";
  private static final String CODE_DOWNSTREAM_UNAVAILABLE = "GATEWAY_DOWNSTREAM_UNAVAILABLE";
  private static final String CODE_DOWNSTREAM_TIMEOUT = "GATEWAY_DOWNSTREAM_TIMEOUT";

  private static final String MSG_ROUTE_NOT_FOUND = "请求的路径不存在";
  private static final String MSG_DOWNSTREAM_UNAVAILABLE = "下游服务暂时不可用，请稍后重试";
  private static final String MSG_DOWNSTREAM_TIMEOUT = "下游服务响应超时，请稍后重试";

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ErrorInfo errorInfo = classify(ex);

    log.error("[GATEWAY] 异常处理：status={} code={} path={} cause={}",
        errorInfo.status.value(), errorInfo.code,
        exchange.getRequest().getPath(), ex.getMessage());

    exchange.getResponse().setStatusCode(errorInfo.status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // ErrorWebExceptionHandler 收到的是 originalExchange，无法直接访问
    // ReactiveUserJwtFilter mutate 后的 request header。
    // 但 exchange.mutate() 构建新 exchange 时 attributes 共享同一 map 实例，
    // 因此优先从 attributes 读 traceId（ReactiveUserJwtFilter.ensureTraceId() 已写入）。
    // 兜底：Client 请求本身就携带了 X-Trace-Id 时，originalExchange header 也有值。
    String traceId = exchange.getAttribute(ReactiveUserJwtFilter.ATTR_TRACE_ID);
    if (traceId == null) {
      traceId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);
    }

    String body = SecurityResponseUtils.buildErrorJson(errorInfo.code, errorInfo.message, traceId);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  /**
   * 将异常分类为对应的 HTTP 状态码和错误码。
   *
   * <p>ResponseStatusException(404) → 路由未找到
   * ConnectException              → 下游不可达 TimeoutException              → 下游超时 其他
   * → 兜底 502
   */
  private ErrorInfo classify(Throwable ex) {
    // 路由未找到：Spring Cloud Gateway 找不到匹配路由时抛出 404 ResponseStatusException
    if (ex instanceof ResponseStatusException rse
        && rse.getStatusCode() == HttpStatus.NOT_FOUND) {
      return new ErrorInfo(HttpStatus.NOT_FOUND, CODE_ROUTE_NOT_FOUND, MSG_ROUTE_NOT_FOUND);
    }

    // 向上遍历 cause 链，查找根本原因
    Throwable cause = ex;
    while (cause != null) {
      if (cause instanceof ConnectException) {
        return new ErrorInfo(HttpStatus.BAD_GATEWAY,
            CODE_DOWNSTREAM_UNAVAILABLE, MSG_DOWNSTREAM_UNAVAILABLE);
      }
      if (cause instanceof TimeoutException) {
        return new ErrorInfo(HttpStatus.GATEWAY_TIMEOUT,
            CODE_DOWNSTREAM_TIMEOUT, MSG_DOWNSTREAM_TIMEOUT);
      }
      cause = cause.getCause();
    }

    // 兜底：其他 Gateway 内部错误按 502 处理
    return new ErrorInfo(HttpStatus.BAD_GATEWAY,
        CODE_DOWNSTREAM_UNAVAILABLE, MSG_DOWNSTREAM_UNAVAILABLE);
  }

  private record ErrorInfo(HttpStatus status, String code, String message) {

  }
}
