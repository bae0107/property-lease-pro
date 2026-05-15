package com.jugu.propertylease.common.pagination.jooq;

import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.common.pagination.core.PageQueryExecutor;
import com.jugu.propertylease.common.pagination.core.PageSlice;
import java.util.List;
import java.util.Optional;
import org.jooq.Field;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
public class JooqPageQueryExecutor implements
    PageQueryExecutor<Object, JooqPageResourceDefinition<Object>> {

  private final DSLContext dsl;
  private final QueryConditionBuilder queryConditionBuilder;

  public JooqPageQueryExecutor(DSLContext dsl, QueryConditionBuilder queryConditionBuilder) {
    this.dsl = dsl;
    this.queryConditionBuilder = queryConditionBuilder;
  }

  @Override
  public PageSlice<Object> execute(JooqPageResourceDefinition<Object> definition,
      PageRequest request) {
    return executeTyped(definition, request);
  }

  public <R> PageSlice<R> executeTyped(JooqPageResourceDefinition<R> definition,
      PageRequest request) {
    Condition dynamicCondition = queryConditionBuilder.build(definition.filterBindings(),
        request.getFilters());
    Condition finalCondition = definition.baseCondition().and(dynamicCondition);
    int pageNo = request.getPageNo();
    int pageSize = request.getPageSize();
    List<Field<?>> groupByFields = definition.groupByFields();

    long total = Optional.ofNullable(groupByFields.isEmpty()
            ? dsl.selectCount().from(definition.from()).where(finalCondition).fetchOne(0, Long.class)
            : dsl.selectCount().from(
                dsl.selectOne().from(definition.from()).where(finalCondition).groupBy(groupByFields)
            ).fetchOne(0, Long.class))
        .orElse(0L);

    var dataQuery = dsl.select(definition.selectFields())
        .from(definition.from())
        .where(finalCondition);

    if (!groupByFields.isEmpty()) {
      dataQuery = dataQuery.groupBy(groupByFields);
    }

    List<R> items = dataQuery
        .orderBy(definition.defaultSorts())
        .limit(pageSize)
        .offset((pageNo - 1) * pageSize)
        .fetch(definition.rowMapper());
    return new PageSlice<>(pageNo, pageSize, total, items);
  }
}
