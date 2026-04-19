package com.jugu.propertylease.security.autoconfigure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.common.model.ErrorResponse;

/**
 * 安全模块统一错误响应 JSON 构建工具。
 *
 * <p>Servlet EntryPoint、ReactiveFilter、Gateway 错误处理共用，保证全链路 JSON 格式绝对一致。
 * 使用 {@link ErrorResponse} 作为统一数据结构，通过 Jackson 序列化，与 common 模块格式对齐。
 *
 * <p>响应格式：
 * <pre>
 * { "code": "IAM_TOKEN_EXPIRED", "message": "Token has expired", "traceId": "abc123" }
 * </pre>
 */
public final class SecurityResponseUtils {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SecurityResponseUtils() {
  }

  /**
   * 构建统一错误响应 JSON 字符串。
   *
   * @param code    错误码，如 {@code IAM_TOKEN_EXPIRED}
   * @param message 可读错误描述
   * @param traceId 链路追踪 ID，可为 null（序列化时忽略）
   */
  public static String buildErrorJson(String code, String message, String traceId) {
    try {
      return MAPPER.writeValueAsString(
          new ErrorResponse().code(code).message(message).traceId(traceId));
    } catch (JsonProcessingException e) {
      // 极端情况兜底（ErrorResponse 字段全为基础类型，正常不会触发）
      return "{\"code\":\"" + escapeBasic(code) + "\",\"message\":\"" + escapeBasic(message)
          + "\"}";
    }
  }

  private static String escapeBasic(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
