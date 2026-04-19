package com.jugu.propertylease.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.model.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * GlobalExceptionHandler 单元测试。
 *
 * <p>traceId 说明：单元测试直接调用 handler 方法，不经过 Spring Filter 链，
 * 因此在 @BeforeEach 中手动 MDC.put 模拟 TraceIdFilter 的注入行为。 生产环境 TraceIdFilter 保证 MDC 始终有值。
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  /**
   * protected 方法测试辅助：提供 WebRequest 参数
   */
  private final WebRequest webRequest =
      new ServletWebRequest(new MockHttpServletRequest());

  @BeforeEach
  void setupMdc() {
    MDC.put("traceId", "test-trace-1");
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  // ===== BusinessException =====

  @Nested
  class BusinessExceptionTests {

    @Test
    void returns_correct_status_and_errorCode() {
      BusinessException ex = new BusinessException(
          HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
      ResponseEntity<ErrorResponse> resp = handler.handleBusiness(ex);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(resp.getBody().getCode()).isEqualTo("USER_NOT_FOUND");
      assertThat(resp.getBody().getMessage()).isEqualTo("用户不存在");
      assertThat(resp.getBody().getTraceId()).isEqualTo("test-trace-1");
    }

    @Test
    void bad_request_variant_returns_400() {
      BusinessException ex = new BusinessException(
          HttpStatus.BAD_REQUEST, "INVENTORY_INSUFFICIENT", "库存不足");
      ResponseEntity<ErrorResponse> resp = handler.handleBusiness(ex);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(resp.getBody().getCode()).isEqualTo("INVENTORY_INSUFFICIENT");
    }
  }

  // ===== handleExceptionInternal：Spring MVC HTTP 类异常统一出口 =====

  @Nested
  class HandleExceptionInternalTests {

    @Test
    void status_404_generates_COMMON_HTTP_404() {
      // 模拟 NoResourceFoundException 场景：父类分发后以 404 调用 handleExceptionInternal
      ResponseEntity<Object> resp = handler.handleExceptionInternal(
          new RuntimeException("No static resource /api/foo"),
          null, new HttpHeaders(), HttpStatus.NOT_FOUND, webRequest);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      ErrorResponse body = (ErrorResponse) resp.getBody();
      assertThat(body.getCode()).isEqualTo("COMMON_HTTP_404");
      assertThat(body.getTraceId()).isEqualTo("test-trace-1");
    }

    @Test
    void status_405_generates_COMMON_HTTP_405() {
      ResponseEntity<Object> resp = handler.handleExceptionInternal(
          new RuntimeException("Method Not Allowed"),
          null, new HttpHeaders(), HttpStatus.METHOD_NOT_ALLOWED, webRequest);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
      assertThat(((ErrorResponse) resp.getBody()).getCode()).isEqualTo("COMMON_HTTP_405");
    }

    @Test
    void status_415_generates_COMMON_HTTP_415() {
      ResponseEntity<Object> resp = handler.handleExceptionInternal(
          new RuntimeException("Unsupported Media Type"),
          null, new HttpHeaders(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, webRequest);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
      assertThat(((ErrorResponse) resp.getBody()).getCode()).isEqualTo("COMMON_HTTP_415");
    }

    @Test
    void status_406_generates_COMMON_HTTP_406() {
      ResponseEntity<Object> resp = handler.handleExceptionInternal(
          new RuntimeException("Not Acceptable"),
          null, new HttpHeaders(), HttpStatus.NOT_ACCEPTABLE, webRequest);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
      assertThat(((ErrorResponse) resp.getBody()).getCode()).isEqualTo("COMMON_HTTP_406");
    }

    @Test
    void errorCode_format_is_COMMON_HTTP_plus_statusCode() {
      // 验证 errorCode 生成规则：COMMON_HTTP_{statusCode}
      ResponseEntity<Object> resp = handler.handleExceptionInternal(
          new RuntimeException("Conflict"),
          null, new HttpHeaders(), HttpStatus.CONFLICT, webRequest);

      assertThat(((ErrorResponse) resp.getBody()).getCode()).isEqualTo("COMMON_HTTP_409");
    }

    @Test
    void response_headers_are_preserved() {
      HttpHeaders headers = new HttpHeaders();
      headers.set("X-Custom-Header", "value");

      ResponseEntity<Object> resp = handler.handleExceptionInternal(
          new RuntimeException("error"),
          null, headers, HttpStatus.BAD_REQUEST, webRequest);

      assertThat(resp.getHeaders().getFirst("X-Custom-Header")).isEqualTo("value");
    }
  }

  // ===== handleMethodArgumentNotValid 覆写 =====

  @Nested
  class MethodArgumentNotValidTests {

    @Test
    void aggregates_all_field_errors_into_message() {
      MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
      // MapBindingResult 不依赖真实 Bean 属性，适合单元测试中构造任意字段错误
      MapBindingResult bindingResult = new MapBindingResult(
          new java.util.HashMap<>(), "obj");
      bindingResult.rejectValue("name", "NotBlank", "姓名不能为空");
      bindingResult.rejectValue("age", "Min", "年龄必须大于 0");
      when(ex.getBindingResult()).thenReturn(bindingResult);

      ResponseEntity<Object> resp = handler.handleMethodArgumentNotValid(
          ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      ErrorResponse body = (ErrorResponse) resp.getBody();
      assertThat(body.getCode()).isEqualTo("COMMON_REQUEST_VALIDATION_FAILED");
      assertThat(body.getMessage()).contains("姓名不能为空");
      assertThat(body.getMessage()).contains("年龄必须大于 0");
      assertThat(body.getTraceId()).isEqualTo("test-trace-1");
    }

    @Test
    void single_field_error_message_is_not_truncated() {
      MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
      MapBindingResult bindingResult = new MapBindingResult(
          new java.util.HashMap<>(), "obj");
      bindingResult.rejectValue("email", "Email", "邮箱格式不正确");
      when(ex.getBindingResult()).thenReturn(bindingResult);

      ResponseEntity<Object> resp = handler.handleMethodArgumentNotValid(
          ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

      assertThat(((ErrorResponse) resp.getBody()).getMessage()).isEqualTo("邮箱格式不正确");
    }
  }

  // ===== handleHttpMessageNotReadable 覆写 =====

  @Nested
  class HttpMessageNotReadableTests {

    @Test
    void returns_400_with_friendly_message_hiding_jackson_detail() {
      HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
          "JSON parse error: Unrecognized field 'foo' (class Bar)",
          new MockHttpInputMessage(new byte[0]));

      ResponseEntity<Object> resp = handler.handleHttpMessageNotReadable(
          ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      ErrorResponse body = (ErrorResponse) resp.getBody();
      assertThat(body.getCode()).isEqualTo("COMMON_REQUEST_BODY_UNREADABLE");
      assertThat(body.getMessage()).isEqualTo("请求体格式错误或缺失");
      // Jackson 内部细节不暴露
      assertThat(body.getMessage()).doesNotContain("JSON parse error");
      assertThat(body.getMessage()).doesNotContain("class Bar");
    }
  }

  // ===== ConstraintViolationException =====

  @Nested
  class ConstraintViolationTests {

    @Test
    void returns_400_with_all_violation_messages() {
      ConstraintViolation<?> v1 = mock(ConstraintViolation.class);
      ConstraintViolation<?> v2 = mock(ConstraintViolation.class);
      when(v1.getMessage()).thenReturn("ID 不能为空");
      when(v2.getMessage()).thenReturn("数量必须大于 0");

      @SuppressWarnings("unchecked")
      ConstraintViolationException ex = new ConstraintViolationException(
          "constraint violation", Set.of(v1, v2));

      ResponseEntity<ErrorResponse> resp = handler.handleConstraintViolation(ex);

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(resp.getBody().getCode()).isEqualTo("COMMON_REQUEST_VALIDATION_FAILED");
      assertThat(resp.getBody().getMessage()).contains("ID 不能为空");
      assertThat(resp.getBody().getMessage()).contains("数量必须大于 0");
    }
  }

  // ===== Exception 兜底 =====

  @Nested
  class FallbackTests {

    @Test
    void returns_500_and_hides_internal_detail() {
      ResponseEntity<ErrorResponse> resp =
          handler.handleUnexpected(new RuntimeException("db connection refused"));

      assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(resp.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
      assertThat(resp.getBody().getMessage()).isEqualTo("服务暂时不可用，请稍后重试");
      assertThat(resp.getBody().getMessage()).doesNotContain("db connection refused");
    }
  }

  // ===== TraceId 行为 =====

  @Nested
  class TraceIdTests {

    @Test
    void business_exception_handler_includes_traceId() {
      MDC.put("traceId", "trace-biz");
      ResponseEntity<ErrorResponse> resp = handler.handleBusiness(
          new BusinessException(HttpStatus.NOT_FOUND, "CODE", "msg"));

      assertThat(resp.getBody().getTraceId()).isEqualTo("trace-biz");
    }

    @Test
    void handleExceptionInternal_includes_traceId() {
      MDC.put("traceId", "trace-http");
      ResponseEntity<Object> resp = handler.handleExceptionInternal(
          new RuntimeException("error"),
          null, new HttpHeaders(), HttpStatus.NOT_FOUND, webRequest);

      assertThat(((ErrorResponse) resp.getBody()).getTraceId()).isEqualTo("trace-http");
    }

    @Test
    void no_traceId_in_mdc_results_in_null_traceId() {
      MDC.remove("traceId");
      ResponseEntity<ErrorResponse> resp = handler.handleBusiness(
          new BusinessException(HttpStatus.BAD_REQUEST, "CODE", "msg"));

      // 生产环境 TraceIdFilter 保证 MDC 有值，此场景仅在单元测试未手动注入时出现
      assertThat(resp.getBody().getTraceId()).isNull();
    }
  }
}
