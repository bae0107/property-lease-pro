package com.jugu.propertylease.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.common.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessExceptionTest {

  @Test
  void constructor_setsAllFields() {
    BusinessException ex = new BusinessException(
        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");
    assertThat(ex.getMessage()).isEqualTo("用户不存在");
  }

  @Test
  void toErrorResponse_mapsFieldsCorrectly() {
    BusinessException ex = new BusinessException(
        HttpStatus.BAD_REQUEST, "INVENTORY_INSUFFICIENT", "库存不足");
    ErrorResponse err = ex.toErrorResponse("trace-abc");
    assertThat(err.getCode()).isEqualTo("INVENTORY_INSUFFICIENT");
    assertThat(err.getMessage()).isEqualTo("库存不足");
    assertThat(err.getTraceId()).isEqualTo("trace-abc");
  }

  @Test
  void toErrorResponse_withNullTraceId_traceIdIsNull() {
    BusinessException ex = new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "服务异常");
    ErrorResponse err = ex.toErrorResponse(null);
    assertThat(err.getTraceId()).isNull();
  }

  @Test
  void isRuntimeException() {
    assertThat(new BusinessException(HttpStatus.BAD_REQUEST, "CODE", "msg"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void constructorWithCause_retainsCause() {
    RuntimeException cause = new RuntimeException("root cause");
    BusinessException ex = new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "不可用", cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
