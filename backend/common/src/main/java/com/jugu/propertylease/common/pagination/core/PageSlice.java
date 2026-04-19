package com.jugu.propertylease.common.pagination.core;

import java.util.List;

public record PageSlice<T>(int pageNo, int pageSize, long total, List<T> items) {

  public PageSlice(int pageNo, int pageSize, long total, List<T> items) {
    this.pageNo = pageNo;
    this.pageSize = pageSize;
    this.total = total;
    this.items = List.copyOf(items);
  }
}
