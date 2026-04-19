package com.jugu.propertylease.common.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.common.model.ErrorResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ResultTest {

  // ===== ok() =====

  @Test
  void ok_isSuccess_statusIs200() {
    Result<String> result = Result.ok("hello");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
    assertThat(result.getData()).isEqualTo("hello");
    assertThat(result.getError()).isNull();
  }

  // ===== fail() =====

  @Test
  void fail_isNotSuccess_holdsError() {
    ErrorResponse err = ErrorResponses.of("USER_NOT_FOUND", "不存在", "t1");
    Result<String> result = Result.fail(err, HttpStatus.NOT_FOUND);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getData()).isNull();
    assertThat(result.getError()).isSameAs(err);
  }

  // ===== of() =====

  @Test
  void of_successSupplier_wrapsInOkResult() {
    Result<Integer> result = Result.of(() -> 42);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getData()).isEqualTo(42);
  }

  @Test
  void of_businessExceptionSupplier_wrapsInFailResult() {
    Result<Integer> result = Result.of(() -> {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在");
    });
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(result.getError().getCode()).isEqualTo("NOT_FOUND");
  }

  @Test
  void of_nonBusinessException_propagatesUpstream() {
    // Result.of 只捕获 BusinessException，其他异常继续向上传播
    assertThatThrownBy(() -> Result.of(() -> {
      throw new RuntimeException("unexpected");
    })).isInstanceOf(RuntimeException.class)
        .hasMessage("unexpected");
  }

  // ===== onFailure() =====

  @Test
  void onFailure_onSuccessResult_handlerNotCalled() {
    AtomicReference<ErrorResponse> captured = new AtomicReference<>();
    Result<String> result = Result.ok("data")
        .onFailure(captured::set);
    assertThat(captured.get()).isNull();
    assertThat(result.getData()).isEqualTo("data");
  }

  @Test
  void onFailure_onFailResult_handlerCalledWithError() {
    AtomicReference<ErrorResponse> captured = new AtomicReference<>();
    ErrorResponse err = ErrorResponses.of("CODE", "msg", null);
    Result.fail(err, HttpStatus.BAD_REQUEST).onFailure(captured::set);
    assertThat(captured.get()).isSameAs(err);
  }

  @Test
  void onFailure_returnsThisForChaining() {
    Result<String> result = Result.fail(
        ErrorResponses.of("CODE", "msg", null), HttpStatus.BAD_REQUEST);
    Result<String> returned = result.onFailure(e -> {
    });
    assertThat(returned).isSameAs(result);
  }

  // ===== getOrElseGet() =====

  @Test
  void getOrElseGet_onSuccess_returnData() {
    String value = Result.ok("original").getOrElseGet(e -> "fallback");
    assertThat(value).isEqualTo("original");
  }

  @Test
  void getOrElseGet_onFailure_returnsFallback() {
    Result<String> fail = Result.fail(ErrorResponses.of("CODE", "msg", null), HttpStatus.NOT_FOUND);
    String value = fail
        .getOrElseGet(errorResponse -> "default-value");
    assertThat(value).isEqualTo("default-value");
  }

  @Test
  void getOrElseGet_fallbackReceivesErrorResponse() {
    AtomicReference<String> capturedCode = new AtomicReference<>();
    Result.fail(ErrorResponses.of("INVENTORY_INSUFFICIENT", "库存不足", null),
            HttpStatus.BAD_REQUEST)
        .getOrElseGet(e -> {
          capturedCode.set(e.getCode());
          return "fallback";
        });
    assertThat(capturedCode.get()).isEqualTo("INVENTORY_INSUFFICIENT");
  }

  // ===== 链式组合 =====

  @Test
  void onFailureThenGetOrElseGet_chainedOnFailure() {
    AtomicReference<String> log = new AtomicReference<>("");
    String value = Result.<String>fail(
            ErrorResponses.of("ERR_CODE", "失败", "trace1"), HttpStatus.BAD_REQUEST)
        .onFailure(e -> log.set("logged:" + e.getCode()))
        .getOrElseGet(e -> "default");

    assertThat(log.get()).isEqualTo("logged:ERR_CODE");
    assertThat(value).isEqualTo("default");
  }

  @Test
  void onFailureThenGetOrElseGet_successSkipsOnFailure() {
    AtomicReference<Boolean> called = new AtomicReference<>(false);
    String value = Result.ok("real-data")
        .onFailure(e -> called.set(true))
        .getOrElseGet(e -> "default");

    assertThat(called.get()).isFalse();
    assertThat(value).isEqualTo("real-data");
  }

  private static class ErrorResponses {

    static ErrorResponse of(String code, String message, String traceId) {
      return new ErrorResponse().code(code).message(message).traceId(traceId);
    }
  }
}
