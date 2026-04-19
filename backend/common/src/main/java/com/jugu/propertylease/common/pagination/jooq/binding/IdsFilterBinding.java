package com.jugu.propertylease.common.pagination.jooq.binding;

import com.jugu.propertylease.common.model.FilterFieldMeta;
import java.util.List;
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.Field;

public final class IdsFilterBinding extends FilterBinding {

  private final Function<List<Long>, Condition> conditionBuilder;

  public IdsFilterBinding(String key, String label,
      Function<List<Long>, Condition> conditionBuilder) {
    super(key, label, FilterType.IDS);
    this.conditionBuilder = conditionBuilder;
  }

  public static IdsFilterBinding inLongs(String key, String label, Field<Long> field) {
    return new IdsFilterBinding(key, label, field::in);
  }

  @Override
  public Condition toIdsCondition(List<Long> value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException();
    }
    return conditionBuilder.apply(value);
  }

  @Override
  public FilterFieldMeta toMeta() {
    return new FilterFieldMeta().key(getKey()).label(getLabel())
        .filterType(FilterFieldMeta.FilterTypeEnum.IDS);
  }
}
