package com.jugu.propertylease.common.pagination.jooq.binding;

import com.jugu.propertylease.common.model.FilterFieldMeta;
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

public final class StringFilterBinding extends FilterBinding {

  private final Function<String, Condition> conditionBuilder;

  public StringFilterBinding(String key, String label,
      Function<String, Condition> conditionBuilder) {
    super(key, label, FilterType.STRING);
    this.conditionBuilder = conditionBuilder;
  }

  public static StringFilterBinding likeIgnoreCase(String key, String label, Field<String> field) {
    return new StringFilterBinding(key, label, v -> DSL.lower(field).eq(v.toLowerCase()));
  }

  @Override
  public Condition toStringCondition(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException();
    }
    return conditionBuilder.apply(value.trim());
  }

  @Override
  public FilterFieldMeta toMeta() {
    return new FilterFieldMeta().key(getKey()).label(getLabel())
        .filterType(FilterFieldMeta.FilterTypeEnum.STRING);
  }
}
