package com.jugu.propertylease.common.pagination.memory;

import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.pagination.core.PageResourceDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public interface MemoryPageResourceDefinition<R> extends PageResourceDefinition<R> {

  List<R> source();

  Comparator<R> defaultComparator();

  Map<String, MemoryFilterBinding<R>> filterBindings();

  @Override
  ListViewMeta listViewMeta();
}
