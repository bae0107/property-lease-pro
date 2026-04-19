package com.jugu.propertylease.security.datapermission;

import com.jugu.propertylease.security.context.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MVC Interceptor：在 preHandle 将 SecurityContext 的 userId 写入 {@link DataPermissionContext}， 在
 * afterCompletion 清除（防止线程池数据污染）。
 *
 * <p>执行顺序：
 * <ol>
 *   <li>Filter（ServletServiceJwtFilter）→ 写入 SecurityContext</li>
 *   <li>Interceptor.preHandle → DataPermissionContext.set(userId)</li>
 *   <li>Controller → Service → Repository → jOOQ VisitListener.visitStart()</li>
 *   <li>Interceptor.afterCompletion → DataPermissionContext.clear()</li>
 * </ol>
 *
 * <p>注册路径 {@code /**}：/internal/** 系统调用时 userId 为 null，VisitListener 不注入条件，行为正确。
 */
public class DataPermissionInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request,
      HttpServletResponse response,
      Object handler) {
    // Filter 已在 SecurityContext 写入 userId，此时可以正确读取
    DataPermissionContext.set(CurrentUser.getCurrentUserId());
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception ex) {
    // 无论是否发生异常，必须清除，防止线程池数据污染
    DataPermissionContext.clear();
  }
}
