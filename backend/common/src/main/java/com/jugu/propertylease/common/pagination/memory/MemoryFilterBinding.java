package com.jugu.propertylease.common.pagination.memory;

import com.jugu.propertylease.common.model.FilterFieldMeta;
import java.util.List;

public abstract class MemoryFilterBinding<R> {

  private final String key;
  private final String label;
  private final FilterFieldMeta.FilterTypeEnum filterType;

  protected MemoryFilterBinding(String key, String label,
      FilterFieldMeta.FilterTypeEnum filterType) {
    this.key = key;
    this.label = label;
    this.filterType = filterType;
  }

  public FilterFieldMeta toMeta() {
    return new FilterFieldMeta().key(key).label(label).filterType(filterType);
  }

  public boolean testString(R row, String value) {
    throw new UnsupportedOperationException();
  }

  public boolean testIds(R row, List<Long> value) {
    throw new UnsupportedOperationException();
  }

  public boolean testEnum(R row, String value) {
    throw new UnsupportedOperationException();
  }
}
