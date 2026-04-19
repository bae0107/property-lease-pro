package com.jugu.propertylease.common.pagination.jooq.schema;

public final class ColumnOverride {

  private final Boolean visible;
  private final Boolean sortable;
  private final String label;
  private final String valueType;

  private ColumnOverride(Builder b) {
    this.visible = b.visible;
    this.sortable = b.sortable;
    this.label = b.label;
    this.valueType = b.valueType;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Boolean getVisible() {
    return visible;
  }

  public Boolean getSortable() {
    return sortable;
  }

  public String getLabel() {
    return label;
  }

  public String getValueType() {
    return valueType;
  }

  public static final class Builder {

    private Boolean visible;
    private Boolean sortable;
    private String label;
    private String valueType;

    public Builder visible(Boolean visible) {
      this.visible = visible;
      return this;
    }

    public Builder sortable(Boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    public Builder label(String label) {
      this.label = label;
      return this;
    }

    public Builder valueType(String valueType) {
      this.valueType = valueType;
      return this;
    }

    public ColumnOverride build() {
      return new ColumnOverride(this);
    }
  }
}
