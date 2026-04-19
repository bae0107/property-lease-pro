package com.jugu.propertylease.security.audit;

import java.time.Instant;

/**
 * 审计事件 POJO。由 {@link AuditLogAspect} 填充，传给 {@link AuditLogger} 写入。
 */
public class AuditEvent {

  private String traceId;
  private Long userId;        // nullable，系统调用时为 null
  private String action;
  private String resource;
  private String resourceId;    // 当前为静态字符串 ⚠️ C-31
  private String beforeJson;    // ⚠️ C-31 待确认，当前固定为 null
  private String afterJson;     // 返回值 JSON 序列化，void 时为 null
  private boolean success;       // true=正常返回，false=抛出异常
  private String errorMessage;  // success=false 时记录异常 message
  private Instant timestamp;     // 方法被 AOP 拦截时记录（执行前）

  private AuditEvent() {
  }

  // ===== Builder =====

  public static Builder builder() {
    return new Builder();
  }

  public String getTraceId() {
    return traceId;
  }

  // ===== Getters =====

  public Long getUserId() {
    return userId;
  }

  public String getAction() {
    return action;
  }

  public String getResource() {
    return resource;
  }

  public String getResourceId() {
    return resourceId;
  }

  public String getBeforeJson() {
    return beforeJson;
  }

  public String getAfterJson() {
    return afterJson;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public static final class Builder {

    private final AuditEvent event = new AuditEvent();

    public Builder traceId(String traceId) {
      event.traceId = traceId;
      return this;
    }

    public Builder userId(Long userId) {
      event.userId = userId;
      return this;
    }

    public Builder action(String action) {
      event.action = action;
      return this;
    }

    public Builder resource(String resource) {
      event.resource = resource;
      return this;
    }

    public Builder resourceId(String resourceId) {
      event.resourceId = resourceId;
      return this;
    }

    public Builder beforeJson(String beforeJson) {
      event.beforeJson = beforeJson;
      return this;
    }

    public Builder afterJson(String afterJson) {
      event.afterJson = afterJson;
      return this;
    }

    public Builder success(boolean success) {
      event.success = success;
      return this;
    }

    public Builder errorMessage(String errorMessage) {
      event.errorMessage = errorMessage;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      event.timestamp = timestamp;
      return this;
    }

    public AuditEvent build() {
      return event;
    }
  }
}
