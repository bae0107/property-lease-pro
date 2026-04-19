package com.jugu.propertylease.security.audit;

/**
 * 审计日志写入接口，各服务在 {@code audit} 包自行实现。
 *
 * <p>实现约定：
 * <ol>
 *   <li>实现类标注 {@code @Component}，置于各服务的 audit 包</li>
 *   <li>使用 {@code @Transactional(propagation = REQUIRES_NEW)} 与主业务事务完全隔离</li>
 *   <li>内部 catch 所有异常并静默处理（log.warn + 不重新抛出）——业务优先原则</li>
 * </ol>
 *
 * <p>为什么 REQUIRES_NEW 有效：
 * {@link AuditLogAspect} 注入的是 Spring AOP 代理的 AuditLogger Bean，
 * {@code @Transactional} 代理可以正确生效，开启独立新事务。
 */
public interface AuditLogger {

  /**
   * 写入审计事件。实现类必须处理内部异常，不允许异常向外传播。
   */
  void log(AuditEvent event);
}
