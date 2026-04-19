package com.jugu.propertylease.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.security.context.CurrentUser;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 审计日志 AOP 切面。
 *
 * <p>{@code @Order(Ordered.LOWEST_PRECEDENCE - 1)} 确保此切面在 {@code @Transactional} 切面
 * 的外层执行：
 * <pre>
 * AuditLogAspect.around 开始（外层，order = MAX_VALUE - 1）
 *   → pjp.proceed()
 *     → @Transactional 开始（内层，order = MAX_VALUE）
 *       → 目标方法执行
 *     → @Transactional 提交/回滚，内层结束
 *   → pjp.proceed() 返回（事务已提交）
 * → auditLogger.log() 在 finally 中调用 ✅
 * </pre>
 *
 * <p>使用 {@code finally} 确保异常场景也记录审计，业务异常重新抛出不被吞掉。
 * {@link AuditLogger#log(AuditEvent)} 内部静默处理写入异常，不影响主业务。
 *
 * <p>此 Bean 由 {@code SecurityStarterAutoConfiguration} 以
 * {@code @ConditionalOnBean(AuditLogger.class)}
 * 条件注册，无 {@link AuditLogger} 实现时不激活。
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class AuditLogAspect {

  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  public AuditLogAspect(AuditLogger auditLogger, ObjectMapper objectMapper) {
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  @Around("@annotation(com.jugu.propertylease.security.audit.AuditLog)")
  public Object around(ProceedingJoinPoint pjp) throws Throwable {
    Instant timestamp = Instant.now();
    MethodSignature sig = (MethodSignature) pjp.getSignature();
    AuditLog annotation = sig.getMethod().getAnnotation(AuditLog.class);

    Object result = null;
    boolean success = true;
    String errorMessage = null;

    try {
      result = pjp.proceed();
      return result;
    } catch (Throwable t) {
      success = false;
      errorMessage = t.getMessage();
      throw t; // 重新抛出，不吞掉业务异常
    } finally {
      // finally 确保无论正常/异常都写审计
      AuditEvent event = AuditEvent.builder()
          .traceId(MDC.get("traceId"))
          .userId(CurrentUser.getCurrentUserId())
          .action(annotation.action())
          .resource(annotation.resource())
          .resourceId(annotation.resourceId())
          .beforeJson(null) // ⚠️ C-31
          .afterJson(serializeSafely(result))
          .success(success)
          .errorMessage(errorMessage)
          .timestamp(timestamp)
          .build();
      try {
        auditLogger.log(event);
      } catch (Exception ignored) {
        // Aspect 层兜底保护：审计失败绝对不能影响主业务。
        // 实现类本应自己静默处理，此处为防御性双重保护。
      }
    }
  }

  private String serializeSafely(Object obj) {
    if (obj == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return "<serialization-failed>";
    }
  }
}
