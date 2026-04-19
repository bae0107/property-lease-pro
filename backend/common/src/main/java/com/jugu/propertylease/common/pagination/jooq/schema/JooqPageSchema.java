package com.jugu.propertylease.common.pagination.jooq.schema;

import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.pagination.jooq.binding.FilterBinding;
import java.util.Map;

public record JooqPageSchema(ListViewMeta listViewMeta, Map<String, FilterBinding> filterBindings) {

  public JooqPageSchema(ListViewMeta listViewMeta, Map<String, FilterBinding> filterBindings) {
    this.listViewMeta = listViewMeta;
    this.filterBindings = Map.copyOf(filterBindings);
  }
}
