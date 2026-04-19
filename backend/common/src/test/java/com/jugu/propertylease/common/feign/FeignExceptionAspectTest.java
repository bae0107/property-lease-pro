package com.jugu.propertylease.common.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import feign.RetryableException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeignExceptionAspectTest {

  private final FeignExceptionAspect aspect = new FeignExceptionAspect();

  @Test
  void successfulProceed_returnsResult() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn("ok");

    Object result = aspect.convertFeignException(pjp);
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void businessException_propagatesAsIs() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    BusinessException original = new BusinessException(
        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "not found");
    when(pjp.proceed()).thenThrow(original);

    assertThatThrownBy(() -> aspect.convertFeignException(pjp))
        .isSameAs(original);
  }

  @Test
  void retryableException_convertsToServiceUnavailable() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    RetryableException retryable = mock(RetryableException.class);
    when(retryable.getMessage()).thenReturn("connection refused");
    when(pjp.proceed()).thenThrow(retryable);

    assertThatThrownBy(() -> aspect.convertFeignException(pjp))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException bex = (BusinessException) ex;
          assertThat(bex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(bex.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        });
  }

  @Test
  void genericException_convertsToServiceUnavailable() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenThrow(new RuntimeException("unexpected feign error"));

    assertThatThrownBy(() -> aspect.convertFeignException(pjp))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException bex = (BusinessException) ex;
          assertThat(bex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(bex.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        });
  }
}
