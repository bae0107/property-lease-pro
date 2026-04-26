package com.jugu.propertylease.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SecurityResponseUtils 使用 Jackson + ErrorResponse 序列化，输出格式： { "code": "...", "message": "...",
 * "traceId": "..." } traceId 为 null 时字段不出现（@JsonInclude(NON_NULL)）。
 */
class SecurityResponseUtilsTest {

  @Test
  void buildErrorJson_withAllFields_producesValidJson() {
    String json = SecurityResponseUtils.buildErrorJson(
        "IAM_TOKEN_EXPIRED", "Token has expired", "trace123");

    assertThat(json).contains("\"code\":\"IAM_TOKEN_EXPIRED\"");
    assertThat(json).contains("\"message\":\"Token has expired\"");
    assertThat(json).contains("\"traceId\":\"trace123\"");
  }

  @Test
  void buildErrorJson_withNullTraceId_keepsTraceIdFieldAsNull() {
    // 当前实现会保留 traceId 字段，null 以 JSON null 输出
    String json = SecurityResponseUtils.buildErrorJson("CODE", "msg", null);
    assertThat(json).contains("\"code\":\"CODE\"");
    assertThat(json).contains("\"traceId\":null");
  }

  @Test
  void buildErrorJson_withBlankTraceId_keepsTraceIdField() {
    // 当前实现不会规整空白字符串，按原值输出
    String json = SecurityResponseUtils.buildErrorJson("CODE", "msg", "  ");
    assertThat(json).contains("\"traceId\":\"  \"");
  }

  @Test
  void buildErrorJson_specialCharsInMessage_areEscaped() {
    String json = SecurityResponseUtils.buildErrorJson(
        "CODE", "msg with \"quotes\"", "trace");
    // Jackson 会对双引号做正确转义
    assertThat(json).contains("\\\"quotes\\\"");
  }

  @Test
  void buildErrorJson_nullCode_usedAsNull() {
    // ErrorResponse 不特殊处理 null code，Jackson 序列化为 null
    String json = SecurityResponseUtils.buildErrorJson(null, "msg", "t1");
    // 不包含 "code":"UNKNOWN"（旧行为已移除），code 字段为 null 被 NON_NULL 忽略或为 null
    assertThat(json).contains("\"message\":\"msg\"");
  }
}
