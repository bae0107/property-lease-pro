package com.jugu.propertylease.security.interceptor;

import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.context.CurrentUser;
import com.jugu.propertylease.security.properties.SecurityProperties;
import com.jugu.propertylease.security.token.ServiceTokenGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * RestClient 出站请求拦截器：自动附加 Service JWT（含当前用户上下文）和 X-Trace-Id。
 *
 * <p>每次请求重新生成 Token（不缓存），确保携带最新的 userId + permissions。
 * userId 为 null 时生成系统级 Token（被调用方 {@code isSystemCall()} 返回 true）。
 *
 * <p>X-Trace-Id 透传规则：
 * <ul>
 *   <li>从 MDC 读取当前请求的 traceId（由 {@code TraceIdFilter} 注入）</li>
 *   <li>若 MDC 中有值，则附加到出站 Header，被调用方 {@code TraceIdFilter} 读到后
 *       继续写入 MDC，实现全链路 traceId 一致</li>
 *   <li>若 MDC 中无值（极端场景），不附加 Header，被调用方自行生成新 traceId</li>
 * </ul>
 */
public class ServiceTokenClientInterceptor implements ClientHttpRequestInterceptor {

  private final SecurityProperties properties;
  private final ServiceTokenGenerator generator;

  public ServiceTokenClientInterceptor(SecurityProperties properties,
      ServiceTokenGenerator generator) {
    this.properties = properties;
    this.generator = generator;
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request,
      byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    // 透传 X-Trace-Id，保证多服务调用链 traceId 一致
    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
      request.getHeaders().set(SecurityConstants.HEADER_TRACE_ID, traceId);
    }

    // mock 模式下 jwt.service 未配置，跳过 Token 附加（被调用方同为 mock 模式，不校验 Token）
    if (properties.getJwt() == null || properties.getJwt().getService() == null) {
      return execution.execute(request, body);
    }

    Long userId = CurrentUser.getCurrentUserId();
    List<String> permissions = new ArrayList<>(CurrentUser.getPermissions());

    String token = generator.generate(
        properties.getServiceName(),
        userId,
        permissions,
        properties.getJwt().getService().getSecret(),
        properties.getJwt().getService().getExpiration());

    request.getHeaders().set(SecurityConstants.HEADER_SERVICE_TOKEN, token);
    return execution.execute(request, body);
  }
}
