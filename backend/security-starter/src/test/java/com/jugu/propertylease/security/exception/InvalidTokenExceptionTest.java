package com.jugu.propertylease.security.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;

class InvalidTokenExceptionTest {

  @Test
  void isInstanceOfAuthenticationException() {
    InvalidTokenException ex = new InvalidTokenException(
        InvalidTokenException.TOKEN_EXPIRED, "expired");
    assertThat(ex).isInstanceOf(AuthenticationException.class);
  }

  @Test
  void getErrorCode_returnsConstructorValue() {
    InvalidTokenException ex = new InvalidTokenException(
        InvalidTokenException.TOKEN_INVALID, "bad sig");
    assertThat(ex.getErrorCode()).isEqualTo(InvalidTokenException.TOKEN_INVALID);
  }

  @Test
  void getMessage_returnsConstructorMessage() {
    InvalidTokenException ex = new InvalidTokenException(
        InvalidTokenException.TOKEN_MALFORMED, "bad format");
    assertThat(ex.getMessage()).isEqualTo("bad format");
  }

  @Test
  void constructor_withCause_preservesCause() {
    RuntimeException cause = new RuntimeException("root");
    InvalidTokenException ex = new InvalidTokenException(
        InvalidTokenException.TOKEN_MALFORMED, "wrap", cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void constants_haveExpectedValues() {
    assertThat(InvalidTokenException.TOKEN_EXPIRED).isEqualTo("IAM_TOKEN_EXPIRED");
    assertThat(InvalidTokenException.TOKEN_INVALID).isEqualTo("IAM_TOKEN_INVALID");
    assertThat(InvalidTokenException.TOKEN_MALFORMED).isEqualTo("IAM_TOKEN_MALFORMED");
    assertThat(InvalidTokenException.TOKEN_MISSING).isEqualTo("IAM_TOKEN_MISSING");
  }
}
