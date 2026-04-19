package com.jugu.propertylease.common.pagination.memory;

import com.jugu.propertylease.common.model.EnumFilter;
import com.jugu.propertylease.common.model.IdsFilter;
import com.jugu.propertylease.common.model.PageRequest;
import com.jugu.propertylease.common.model.StringFilter;
import com.jugu.propertylease.common.pagination.core.PageQueryExecutor;
import com.jugu.propertylease.common.pagination.core.PageSlice;
import java.util.List;
import java.util.stream.Stream;

public final class MemoryPageQueryExecutor<R> implements
    PageQueryExecutor<R, MemoryPageResourceDefinition<R>> {

  @Override
  public PageSlice<R> execute(MemoryPageResourceDefinition<R> definition, PageRequest request) {
    int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
    int pageSize = request.getPageSize() == null ? 20 : request.getPageSize();
    Stream<R> stream = definition.source().stream();
    if (request.getFilters() != null && request.getFilters().getStringFilters() != null) {
      for (StringFilter f : request.getFilters().getStringFilters()) {
        MemoryFilterBinding<R> b = definition.filterBindings().get(f.getKey());
        if (b != null) {
          stream = stream.filter(r -> b.testString(r, f.getValue()));
        }
      }
    }
    if (request.getFilters() != null && request.getFilters().getIdsFilters() != null) {
      for (IdsFilter f : request.getFilters().getIdsFilters()) {
        MemoryFilterBinding<R> b = definition.filterBindings().get(f.getKey());
        if (b != null) {
          stream = stream.filter(r -> b.testIds(r, f.getValue()));
        }
      }
    }
    if (request.getFilters() != null && request.getFilters().getEnumFilters() != null) {
      for (EnumFilter f : request.getFilters().getEnumFilters()) {
        MemoryFilterBinding<R> b = definition.filterBindings().get(f.getKey());
        if (b != null) {
          stream = stream.filter(r -> b.testEnum(r, f.getValue()));
        }
      }
    }
    List<R> all = stream.sorted(definition.defaultComparator()).toList();
    int from = Math.min((pageNo - 1) * pageSize, all.size());
    int to = Math.min(from + pageSize, all.size());
    return new PageSlice<>(pageNo, pageSize, all.size(), all.subList(from, to));
  }
}
