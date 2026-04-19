package com.jugu.propertylease.common.pagination.jooq.binding;

import com.jugu.propertylease.common.model.FilterFieldMeta;
import com.jugu.propertylease.common.model.FilterOption;
import java.util.List;
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.Field;

public final class EnumFilterBinding extends FilterBinding {

  private final Function<String, Condition> conditionBuilder;
  private final List<FilterOption> options;

  public EnumFilterBinding(String key, String label, Function<String, Condition> conditionBuilder,
      List<FilterOption> options) {
    super(key, label, FilterType.ENUM);
    this.conditionBuilder = conditionBuilder;
    this.options = List.copyOf(options);
  }

  public static EnumFilterBinding eqString(String key, String label, Field<String> field,
      List<FilterOption> options) {
    return new EnumFilterBinding(key, label, field::eq, options);
  }

  @Override
  public Condition toEnumCondition(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException();
    }
    if (options.stream().noneMatch(it -> value.equals(it.getValue()))) {
      throw new IllegalArgumentException();
    }
    return conditionBuilder.apply(value);
  }

  @Override
  public FilterFieldMeta toMeta() {
    return new FilterFieldMeta().key(getKey()).label(getLabel())
        .filterType(FilterFieldMeta.FilterTypeEnum.ENUM)
        .options(options);
  }
}
