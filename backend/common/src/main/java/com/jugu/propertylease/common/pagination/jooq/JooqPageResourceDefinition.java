package com.jugu.propertylease.common.pagination.jooq;

import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.pagination.core.PageResourceDefinition;
import com.jugu.propertylease.common.pagination.jooq.binding.FilterBinding;
import com.jugu.propertylease.common.pagination.jooq.schema.JooqPageSchema;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.RecordMapper;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.TableLike;

public interface JooqPageResourceDefinition<R> extends PageResourceDefinition<R> {

  List<SelectFieldOrAsterisk> selectFields();

  TableLike<?> from();

  Condition baseCondition();

  List<SortField<?>> defaultSorts();

  RecordMapper<org.jooq.Record, R> rowMapper();

  JooqPageSchema pageSchema();

  default Map<String, FilterBinding> filterBindings() {
    return pageSchema().filterBindings();
  }

  @Override
  default ListViewMeta listViewMeta() {
    return pageSchema().listViewMeta();
  }
}
