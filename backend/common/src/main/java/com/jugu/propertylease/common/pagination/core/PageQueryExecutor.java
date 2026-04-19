package com.jugu.propertylease.common.pagination.core;

import com.jugu.propertylease.common.model.PageRequest;

public interface PageQueryExecutor<R, D extends PageResourceDefinition<R>> {

  PageSlice<R> execute(D definition, PageRequest request);
}
