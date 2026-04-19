package com.jugu.propertylease.security.datapermission;

/**
 * 数据权限 ThreadLocal 容器。
 *
 * <p>使用 {@code remove()} 而非 {@code set(null)} 防止线程池内存泄漏。
 * 所有方法为静态方法，{@link DataPermissionInterceptor} 和 jOOQ VisitListener 直接调用。
 */
public final class DataPermissionContext {

  private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();

  private DataPermissionContext() {
  }

  /**
   * 设置当前线程的 userId（null 表示系统调用，不注入数据权限条件）。
   */
  public static void set(Long userId) {
    CONTEXT.set(userId);
  }

  /**
   * 获取当前线程的 userId，null 表示系统调用或未设置。
   */
  public static Long get() {
    return CONTEXT.get();
  }

  /**
   * 清除当前线程数据，必须在请求结束时调用（即使发生异常）。
   */
  public static void clear() {
    CONTEXT.remove();
  }
}
