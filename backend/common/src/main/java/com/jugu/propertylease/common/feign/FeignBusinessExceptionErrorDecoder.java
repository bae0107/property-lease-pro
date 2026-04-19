package com.jugu.propertylease.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.model.ErrorResponse;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * 全局 Feign ErrorDecoder：将下游服务的 4xx/5xx 响应统一转换为 {@link BusinessException}， 保留原始 {@link HttpStatus} 和
 * errorCode/traceId。
 *
 * <p>覆盖范围：所有收到 HTTP 响应的失败（有 status code）。
 * 连接失败（{@code RetryableException}）由 {@link FeignExceptionAspect} 处理。
 *
 * <p>响应体解析策略：
 * <ol>
 *   <li>尝试将响应体反序列化为 {@link ErrorResponse}</li>
 *   <li>解析失败（如网关返回 HTML）→ 使用 {@code DOWNSTREAM_ERROR} 兜底</li>
 * </ol>
 */
public class FeignBusinessExceptionErrorDecoder implements ErrorDecoder {

  private static final Logger log = LoggerFactory.getLogger(
      FeignBusinessExceptionErrorDecoder.class);

  private final ObjectMapper objectMapper;

  public FeignBusinessExceptionErrorDecoder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Exception decode(String methodKey, Response response) {
    HttpStatus status = HttpStatus.resolve(response.status());
    if (status == null) {
      log.warn("method = {},why no http stutus", methodKey);
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    ErrorResponse errorResponse = parseBody(response);

    log.debug("Feign 错误响应: method={} status={} code={}",
        methodKey, response.status(), errorResponse.getCode());

    return new BusinessException(status, errorResponse.getCode(), errorResponse.getMessage());
  }

  private ErrorResponse parseBody(Response response) {
    if (response.body() == null) {
      return new ErrorResponse().code("DOWNSTREAM_ERROR").message("下游服务返回错误，响应体为空");
    }
    try (InputStream is = response.body().asInputStream()) {
      return objectMapper.readValue(is, ErrorResponse.class);
    } catch (IOException e) {
      // 响应体不是 ErrorResponse 格式（如 502 网关返回 HTML）
      return new ErrorResponse().code("DOWNSTREAM_ERROR").message("下游服务暂时不可用，请稍后重试");
    }
  }
}
