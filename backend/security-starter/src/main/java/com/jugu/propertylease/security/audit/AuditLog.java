package com.jugu.propertylease.security.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Service 层写操作方法上，触发 {@link AuditLogAspect} 自动采集审计事件。
 *
 * <pre>
 * &#64;AuditLog(action = "CREATE_ORDER", resource = "ORDER")
 * &#64;Transactional
 * public OrderResponse createOrder(CreateOrderRequest request) { ... }
 * </pre>
 *
 * <p>⚠️ C-31：{@code resourceId} 当前为静态字符串，动态提取方案待后续确认。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

  /**
   * 操作动词，如 "CREATE_ORDER"、"UPDATE_DEVICE_STATUS"
   */
  String action();

  /**
   * 资源类型，如 "ORDER"、"DEVICE"
   */
  String resource();

  /**
   * 资源 ID（当前为静态字符串占位）⚠️ C-31
   */
  String resourceId() default "";
}
