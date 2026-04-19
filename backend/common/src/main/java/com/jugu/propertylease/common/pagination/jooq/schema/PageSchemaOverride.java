package com.jugu.propertylease.common.pagination.jooq.schema;

import com.jugu.propertylease.common.model.FilterOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public final class PageSchemaOverride {

  private final Set<String> includeColumns;
  private final Set<String> filterableColumns;
  private final Map<String, String> keyOverrides;
  private final Map<String, String> labelOverrides;
  private final Map<String, EnumOverride> enumOverrides;
  private final Map<String, ColumnOverride> columnOverrides;
  private final List<String> columnOrder;

  private PageSchemaOverride(Builder b) {
    this.includeColumns = Collections.unmodifiableSet(new LinkedHashSet<>(b.includeColumns));
    this.filterableColumns = Collections.unmodifiableSet(new LinkedHashSet<>(b.filterableColumns));
    this.keyOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(b.keyOverrides));
    this.labelOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(b.labelOverrides));
    this.enumOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(b.enumOverrides));
    this.columnOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(b.columnOverrides));
    this.columnOrder = List.copyOf(b.columnOrder);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private final Set<String> includeColumns = new LinkedHashSet<>();
    private final Set<String> filterableColumns = new LinkedHashSet<>();
    private final Map<String, String> keyOverrides = new LinkedHashMap<>();
    private final Map<String, String> labelOverrides = new LinkedHashMap<>();
    private final Map<String, EnumOverride> enumOverrides = new LinkedHashMap<>();
    private final Map<String, ColumnOverride> columnOverrides = new LinkedHashMap<>();
    private List<String> columnOrder = List.of();

    public Builder includeColumns(Collection<String> columns) {
      includeColumns.addAll(columns);
      return this;
    }

    public Builder filterableColumns(Collection<String> columns) {
      filterableColumns.addAll(columns);
      return this;
    }

    public Builder keyOverride(String originalKey, String targetKey) {
      keyOverrides.put(originalKey, targetKey);
      return this;
    }

    public Builder labelOverride(String key, String label) {
      labelOverrides.put(key, label);
      return this;
    }

    public Builder enumOverride(String key, List<FilterOption> options) {
      enumOverrides.put(key, new EnumOverride(options));
      return this;
    }

    public Builder columnOverride(String key, ColumnOverride override) {
      columnOverrides.put(key, override);
      return this;
    }

    public Builder columnOrder(List<String> orderedKeys) {
      columnOrder = List.copyOf(orderedKeys);
      return this;
    }

    public PageSchemaOverride build() {
      return new PageSchemaOverride(this);
    }
  }
}
