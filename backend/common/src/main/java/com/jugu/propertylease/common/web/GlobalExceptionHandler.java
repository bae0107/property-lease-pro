package com.jugu.propertylease.common.web;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.model.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 全局异常处理器，统一将异常转换为 {@link ErrorResponse} 格式。
 *
 * <p>继承 {@link ResponseEntityExceptionHandler}：Spring 官方扩展点，内部维护了所有
 * Spring MVC HTTP 类异常的完整列表，并通过单一 final {@code @ExceptionHandler} 方法 统一分发到各 protected 方法，最终汇聚到
 * {@link #handleExceptionInternal}。 覆写该方法即可统一定制所有 Spring MVC HTTP 类异常的响应格式，Spring 升级新增的同类异常
 * 自动覆盖，无需修改代码。
 *
 * <p>处理策略：
 * <ul>
 *   <li>{@link BusinessException}：业务逻辑失败，使用其自带的 HttpStatus 和 errorCode</li>
 *   <li>{@link #handleExceptionInternal} 覆写：统一处理所有 Spring MVC HTTP 类异常
 *       （404 NoResourceFoundException、405、415、406、400 缺失参数等），
 *       errorCode 格式：{@code COMMON_HTTP_{statusCode}}</li>
 *   <li>{@link #handleMethodArgumentNotValid} 覆写：{@code @Valid} 字段级校验失败，
 *       需聚合所有字段错误信息，单独定制</li>
 *   <li>{@link #handleHttpMessageNotReadable} 覆写：请求体不可读，需隐藏 Jackson 细节</li>
 *   <li>{@link ConstraintViolationException}：Bean Validation API 异常，
 *       不走父类 handler，单独处理</li>
 *   <li>{@link Exception} 兜底：500，隐藏内部细节，完整堆栈写日志</li>
 * </ul>
 *
 * <p>traceId 从 MDC 读取（由 {@code TraceIdFilter} 在 SecurityFilterChain 中注入）。
 * 生产环境 TraceIdFilter 保证 MDC 始终有值；单元测试不经过 Filter 链时需在
 * {@code @BeforeEach} 中手动 {@code MDC.put("traceId", ...)}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // ===== 业务异常 =====

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
    log.debug("业务异常: status={} code={} message={}",
        ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage());
    return ResponseEntity
        .status(ex.getHttpStatus())
        .body(ex.toErrorResponse(traceId()));
  }

  // ===== Spring MVC HTTP 类异常统一出口 =====

  /**
   * 所有 Spring MVC HTTP 类异常的统一定制出口。
   *
   * <p>父类 {@link ResponseEntityExceptionHandler} 内有一个 final 的 {@code @ExceptionHandler}
   * 方法，负责接收并分发所有已知的 Spring MVC 异常（404 NoResourceFoundException、 405、415、406、400 缺失参数等）到各 protected
   * 方法，最终统一调用此方法。 覆写此方法即实现"一处定制，全部覆盖"。
   *
   * <p>errorCode 格式：{@code COMMON_HTTP_{statusCode}}，
   * 如 {@code COMMON_HTTP_404}、{@code COMMON_HTTP_405}。
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers,
      HttpStatusCode status, WebRequest request) {
    String code = "COMMON_HTTP_" + status.value();
    Object responseBody = toErrorResponse(code, ex.getMessage());
    return ResponseEntity.status(status).headers(headers).body(responseBody);
  }

  // ===== 需要定制逻辑的覆写方法 =====

  /**
   * 400：{@code @Valid} / {@code @Validated} 字段级校验失败。
   *
   * <p>虽然最终也调用 {@link #handleExceptionInternal}，但需要在调用前
   * 聚合所有 {@link FieldError} 信息，因此单独覆写，不走通用出口。
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers,
      HttpStatusCode status, WebRequest request) {
    String details = ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("; "));
    Object responseBody = toErrorResponse(
        "COMMON_REQUEST_VALIDATION_FAILED", details);
    return ResponseEntity.status(status).headers(headers).body(responseBody);
  }

  /**
   * 400：请求体不可读（JSON 格式错误、缺失等）。
   *
   * <p>需要隐藏原始 Jackson 错误细节，替换为对客户端友好的描述。
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpHeaders headers,
      HttpStatusCode status, WebRequest request) {
    Object responseBody = toErrorResponse(
        "COMMON_REQUEST_BODY_UNREADABLE", "请求体格式错误或缺失");
    return ResponseEntity.status(status).headers(headers).body(responseBody);
  }

  // ===== 非 Spring MVC 体系的异常 =====

  /**
   * 400：方法参数 {@code @Validated} 校验失败（作用于单个参数，非 @RequestBody）。
   *
   * <p>{@link ConstraintViolationException} 来自 Bean Validation API，不继承 Spring 体系，
   * 不走父类 handler，需单独注册。
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex) {
    String details = ex.getConstraintViolations().stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.joining("; "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(toErrorResponse("COMMON_REQUEST_VALIDATION_FAILED", details));
  }

  // ===== 兜底 =====

  /**
   * 500：所有未预期异常，隐藏内部细节，完整堆栈写日志
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("未预期异常，traceId={}", traceId(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(toErrorResponse(
            "INTERNAL_SERVER_ERROR", "服务暂时不可用，请稍后重试"));
  }

  // ===== 工具方法 =====

  private String traceId() {
    return MDC.get("traceId");
  }

  private ErrorResponse toErrorResponse(String code, String message) {
    return new ErrorResponse().code(code).message(message).traceId(MDC.get("traceId"));
  }
}
