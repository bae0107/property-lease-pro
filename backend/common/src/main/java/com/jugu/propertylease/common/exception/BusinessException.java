package com.jugu.propertylease.common.exception;

import com.jugu.propertylease.common.model.ErrorResponse;
import org.springframework.http.HttpStatus;

/**
 * 业务异常，携带 HTTP 状态码和结构化错误码。
 *
 * <p>设计原则：
 * <ul>
 *   <li>继承 {@link RuntimeException}，调用方可以选择处理，也可以透传给
 *       {@code GlobalExceptionHandler} 统一兜底。</li>
 *   <li>持有 {@link HttpStatus}，{@code GlobalExceptionHandler} 直接读取并写入响应状态码。</li>
 *   <li>errorCode 遵循 {@code MODULE_RESOURCE_REASON} 格式，如 {@code USER_NOT_FOUND}。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户 ID 999 不存在");
 * throw new BusinessException(HttpStatus.BAD_REQUEST, "INVENTORY_INSUFFICIENT", "库存不足");
 * </pre>
 */
public class BusinessException extends RuntimeException {

  private final HttpStatus httpStatus;
  private final String errorCode;

  public BusinessException(HttpStatus httpStatus, String errorCode, String message) {
    super(message);
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
  }

  public BusinessException(HttpStatus httpStatus, String errorCode, String message,
      Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
  }

  /**
   * 转换为 {@link ErrorResponse}（traceId 由调用方从 MDC 注入）。
   */
  public ErrorResponse toErrorResponse(String traceId) {
    return new ErrorResponse().code(errorCode).message(getMessage()).traceId(traceId);
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
