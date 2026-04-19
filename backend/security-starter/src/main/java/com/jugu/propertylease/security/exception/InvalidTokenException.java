package com.jugu.propertylease.security.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Token 校验失败异常。
 *
 * <p>继承 {@link AuthenticationException}，Spring Security 的 {@code AuthenticationEntryPoint}
 * 能自动捕获并返回 401 JSON，无需在 ExceptionHandler 中单独处理。 Filter 层在 Token 缺失/格式错误时直接 throw 此异常，由 EntryPoint
 * 统一处理。
 */
public class InvalidTokenException extends AuthenticationException {

  // ===== errorCode 内置常量 =====

  /**
   * Token 已过期
   */
  public static final String TOKEN_EXPIRED = "IAM_TOKEN_EXPIRED";

  /**
   * Token 签名不匹配
   */
  public static final String TOKEN_INVALID = "IAM_TOKEN_INVALID";

  /**
   * Token 格式/解析错误
   */
  public static final String TOKEN_MALFORMED = "IAM_TOKEN_MALFORMED";

  /**
   * Token 缺失
   */
  public static final String TOKEN_MISSING = "IAM_TOKEN_MISSING";

  // ===== Fields =====

  private final String errorCode;

  // ===== Constructors =====

  public InvalidTokenException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public InvalidTokenException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  // ===== Getter =====

  public String getErrorCode() {
    return errorCode;
  }
}
