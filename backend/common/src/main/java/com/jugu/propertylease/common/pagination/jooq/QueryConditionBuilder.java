package com.jugu.propertylease.common.pagination.jooq;

import com.jugu.propertylease.common.model.EnumFilter;
import com.jugu.propertylease.common.model.IdsFilter;
import com.jugu.propertylease.common.model.QueryFilter;
import com.jugu.propertylease.common.model.StringFilter;
import com.jugu.propertylease.common.pagination.jooq.binding.FilterBinding;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class QueryConditionBuilder {

  public Condition build(Map<String, FilterBinding> bindings, QueryFilter filters) {
    Condition condition = DSL.trueCondition();
    if (filters == null) {
      return condition;
    }
    if (filters.getStringFilters() != null) {
      for (StringFilter f : filters.getStringFilters()) {
        condition = condition.and(
            requireBinding(bindings, f.getKey()).toStringCondition(f.getValue()));
      }
    }
    if (filters.getIdsFilters() != null) {
      for (IdsFilter f : filters.getIdsFilters()) {
        condition = condition.and(
            requireBinding(bindings, f.getKey()).toIdsCondition(f.getValue()));
      }
    }
    if (filters.getEnumFilters() != null) {
      for (EnumFilter f : filters.getEnumFilters()) {
        condition = condition.and(
            requireBinding(bindings, f.getKey()).toEnumCondition(f.getValue()));
      }
    }
    return condition;
  }

  protected FilterBinding requireBinding(Map<String, FilterBinding> bindings, String key) {
    FilterBinding b = bindings.get(key);
    if (b == null) {
      throw new IllegalArgumentException("Invalid filter key: " + key);
    }
    return b;
  }
}
