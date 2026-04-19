package com.jugu.propertylease.common.pagination.jooq.binding;

import com.jugu.propertylease.common.model.FilterFieldMeta;
import java.util.List;
import org.jooq.Condition;

public abstract class FilterBinding {

  private final String key;
  private final String label;
  private final FilterType filterType;

  protected FilterBinding(String key, String label, FilterType filterType) {
    this.key = key;
    this.label = label;
    this.filterType = filterType;
  }

  public String getKey() {
    return key;
  }

  public String getLabel() {
    return label;
  }

  public FilterType getFilterType() {
    return filterType;
  }

  public abstract FilterFieldMeta toMeta();

  public Condition toStringCondition(String value) {
    throw new UnsupportedOperationException();
  }

  public Condition toIdsCondition(List<Long> value) {
    throw new UnsupportedOperationException();
  }

  public Condition toEnumCondition(String value) {
    throw new UnsupportedOperationException();
  }

  public enum FilterType {STRING, IDS, ENUM}
}
