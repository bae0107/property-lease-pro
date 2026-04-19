package com.jugu.propertylease.common.pagination.jooq.schema;

import com.jugu.propertylease.common.model.FilterOption;
import java.util.List;

public record EnumOverride(List<FilterOption> options) {

  public EnumOverride(List<FilterOption> options) {
    this.options = List.copyOf(options);
  }
}
