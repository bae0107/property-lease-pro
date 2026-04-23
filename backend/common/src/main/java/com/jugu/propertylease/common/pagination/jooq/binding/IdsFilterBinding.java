package com.jugu.propertylease.common.pagination.jooq.binding;

import com.jugu.propertylease.common.model.FilterFieldMeta;
import com.jugu.propertylease.common.model.FilterOption;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.Field;

public final class IdsFilterBinding extends FilterBinding {

  private final Function<List<Long>, Condition> conditionBuilder;
  private final List<FilterOption> options;
  private final List<String> dependsOn;
  private final boolean showAllWhenParentEmpty;
  private final Map<String, List<String>> allowedOptionValuesByParent;

  public IdsFilterBinding(String key, String label,
      Function<List<Long>, Condition> conditionBuilder) {
    this(key, label, conditionBuilder, List.of(), List.of(), true, Map.of());
  }

  public IdsFilterBinding(String key, String label,
      Function<List<Long>, Condition> conditionBuilder,
      List<FilterOption> options,
      List<String> dependsOn,
      boolean showAllWhenParentEmpty,
      Map<String, List<String>> allowedOptionValuesByParent) {
    super(key, label, FilterType.IDS);
    this.conditionBuilder = conditionBuilder;
    this.options = List.copyOf(options);
    this.dependsOn = List.copyOf(dependsOn);
    this.showAllWhenParentEmpty = showAllWhenParentEmpty;
    this.allowedOptionValuesByParent = Map.copyOf(allowedOptionValuesByParent);
  }

  public static IdsFilterBinding inLongs(String key, String label, Field<Long> field) {
    return new IdsFilterBinding(key, label, field::in);
  }

  public static IdsFilterBinding inLongsWithOptions(String key, String label, Field<Long> field,
      List<FilterOption> options, List<String> dependsOn, boolean showAllWhenParentEmpty,
      Map<String, List<String>> allowedOptionValuesByParent) {
    return new IdsFilterBinding(key, label, field::in, options, dependsOn, showAllWhenParentEmpty,
        allowedOptionValuesByParent);
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
        .filterType(FilterFieldMeta.FilterTypeEnum.IDS)
        .selectionMode(FilterFieldMeta.SelectionModeEnum.SINGLE)
        .valueType(FilterFieldMeta.ValueTypeEnum.LONG)
        .options(options.isEmpty() ? null : options)
        .dependsOn(dependsOn.isEmpty() ? null : dependsOn)
        .showAllWhenParentEmpty(showAllWhenParentEmpty)
        .allowedOptionValuesByParent(allowedOptionValuesByParent.isEmpty()
            ? null : allowedOptionValuesByParent);
  }
}
