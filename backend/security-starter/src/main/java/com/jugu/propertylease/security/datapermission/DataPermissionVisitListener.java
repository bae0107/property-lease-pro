package com.jugu.propertylease.security.datapermission;

import org.jooq.DeleteQuery;
import org.jooq.QueryPart;
import org.jooq.SelectQuery;
import org.jooq.UpdateQuery;
import org.jooq.VisitContext;
import org.jooq.VisitListener;

/**
 * 数据权限 jOOQ VisitListener 抽象基类。
 *
 * <p>在每次 SQL 执行前（visitStart），从 {@link DataPermissionContext} 获取当前 userId，
 * 对 SELECT / UPDATE / DELETE 注入数据权限条件，防止越权读取和越权修改。 INSERT 不注入（新资源的 userId 由业务 Service 赋值）。
 *
 * <p>只在根查询（queryParts.length == 1）时触发，避免对子查询重复注入。
 *
 * <p>各服务实现此抽象类，标注 {@code @Component}，并在 JooqConfig 中注册到 DSLContext。
 *
 * <pre>
 * // 各服务 JooqConfig 示例：
 * &#64;Bean
 * public DefaultConfigurationCustomizer jooqCustomizer(BillingDataPermissionVisitListener listener) {
 *     return config -> config.set(new VisitListenerProvider[]{ () -> listener });
 * }
 * </pre>
 *
 * <p>⚠️ C-43：DefaultConfigurationCustomizer API 在 jOOQ 3.19 + Spring Boot 3.2 中的
 * 准确包名需开发时确认。
 */
public abstract class DataPermissionVisitListener implements VisitListener {

  @Override
  public void visitStart(VisitContext context) {
    Long userId = DataPermissionContext.get();
    if (userId == null) {
      // 系统调用（Service Token 无 userId）或未认证，不注入任何条件
      return;
    }

    QueryPart[] parts = context.queryParts();
    // 只在根查询（最外层）注入，防止对子查询重复注入
    if (parts == null || parts.length != 1) {
      return;
    }

    QueryPart root = parts[0];
    if (root instanceof SelectQuery<?> q) {
      applySelectPermission(q, userId);
    } else if (root instanceof UpdateQuery<?> q) {
      applyUpdatePermission(q, userId);
    } else if (root instanceof DeleteQuery<?> q) {
      applyDeletePermission(q, userId);
    }
    // InsertQuery: 不处理，所有权由业务 Service 层赋值
  }

  @Override
  public void visitEnd(VisitContext context) {
    // 默认空实现，子类可 override
  }

  /**
   * 注入 SELECT 查询的数据权限条件。
   *
   * <p>示例：{@code query.addConditions(ORDERS.USER_ID.eq(userId))}
   */
  protected abstract void applySelectPermission(SelectQuery<?> query, Long userId);

  /**
   * 注入 UPDATE 查询的数据权限条件，防止越权修改。
   */
  protected abstract void applyUpdatePermission(UpdateQuery<?> query, Long userId);

  /**
   * 注入 DELETE 查询的数据权限条件，防止越权删除。
   */
  protected abstract void applyDeletePermission(DeleteQuery<?> query, Long userId);
}
