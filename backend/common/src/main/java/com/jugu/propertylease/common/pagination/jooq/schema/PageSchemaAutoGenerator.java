package com.jugu.propertylease.common.pagination.jooq.schema;

import com.jugu.propertylease.common.model.ColumnMeta;
import com.jugu.propertylease.common.model.FilterFieldMeta;
import com.jugu.propertylease.common.model.ListViewMeta;
import com.jugu.propertylease.common.pagination.jooq.binding.EnumFilterBinding;
import com.jugu.propertylease.common.pagination.jooq.binding.FilterBinding;
import com.jugu.propertylease.common.pagination.jooq.binding.IdsFilterBinding;
import com.jugu.propertylease.common.pagination.jooq.binding.StringFilterBinding;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.stereotype.Component;

@Component
public class PageSchemaAutoGenerator {

  public JooqPageSchema generate(Table<?> table) {
    return generate(table, PageSchemaOverride.builder().build());
  }

  public JooqPageSchema generate(Table<?> table, PageSchemaOverride override) {
    List<Descriptor> ds = introspect(table, override);
    Map<String, FilterBinding> bindings = inferDefaultBindings(ds);
    applyEnumOverrides(ds, bindings, override);
    applyFilterableColumns(bindings, override);
    List<ColumnMeta> columns = inferDefaultColumns(ds);
    columns = applyColumnOverrides(columns, override);
    columns = applyColumnIncludes(columns, override);
    columns = applyColumnOrder(columns, override);
    List<FilterFieldMeta> filters = bindings.values().stream().map(FilterBinding::toMeta).toList();
    return new JooqPageSchema(new ListViewMeta().filters(filters).columns(columns), bindings);
  }

  private List<Descriptor> introspect(Table<?> table, PageSchemaOverride override) {
    List<Descriptor> list = new ArrayList<>();
    for (Field<?> f : table.fields()) {
      String originalKey = toCamelCase(f.getName());
      String effectiveKey = override.getKeyOverrides().getOrDefault(originalKey, originalKey);
      String label = override.getLabelOverrides()
          .getOrDefault(effectiveKey, defaultLabel(effectiveKey));
      list.add(new Descriptor(f, originalKey, effectiveKey, label));
    }
    return list;
  }

  private Map<String, FilterBinding> inferDefaultBindings(List<Descriptor> ds) {
    Map<String, FilterBinding> r = new LinkedHashMap<>();
    for (Descriptor d : ds) {
      Field<?> f = d.field();
      String key = d.key();
      String label = d.label();
      if (isIdField(f)) {
        @SuppressWarnings("unchecked") Field<Long> lf = (Field<Long>) f;
        r.put(key, IdsFilterBinding.inLongs(key, label, lf));
      } else if (isStringField(f) && !isEnumCandidate(f)) {
        @SuppressWarnings("unchecked") Field<String> sf = (Field<String>) f;
        r.put(key, StringFilterBinding.likeIgnoreCase(key, label, sf));
      }
    }
    return r;
  }

  private void applyEnumOverrides(List<Descriptor> ds, Map<String, FilterBinding> bindings,
      PageSchemaOverride override) {
    for (var e : override.getEnumOverrides().entrySet()) {
      Descriptor d = ds.stream().filter(it -> it.key().equals(e.getKey())).findFirst()
          .orElseThrow();
      @SuppressWarnings("unchecked") Field<String> f = (Field<String>) d.field();
      bindings.put(e.getKey(),
          EnumFilterBinding.eqString(e.getKey(), d.label(), f, e.getValue().options()));
    }
  }

  private void applyFilterableColumns(Map<String, FilterBinding> bindings,
      PageSchemaOverride override) {
    if (!override.getFilterableColumns().isEmpty()) {
      bindings.keySet().retainAll(override.getFilterableColumns());
    }
  }

  private List<ColumnMeta> inferDefaultColumns(List<Descriptor> ds) {
    List<ColumnMeta> cols = new ArrayList<>();
    for (Descriptor d : ds) {
      cols.add(new ColumnMeta().key(d.key()).label(d.label()).valueType(inferValueType(d.field()))
          .visible(true)
          .sortable(isSortable(d.field())));
    }
    return cols;
  }

  private List<ColumnMeta> applyColumnOverrides(List<ColumnMeta> cols,
      PageSchemaOverride override) {
    List<ColumnMeta> out = new ArrayList<>();
    for (ColumnMeta c : cols) {
      ColumnOverride co = override.getColumnOverrides().get(c.getKey());
      if (co == null) {
        out.add(c);
        continue;
      }
      out.add(new ColumnMeta().key(c.getKey())
          .label(co.getLabel() != null ? co.getLabel() : c.getLabel()).valueType(
              co.getValueType() != null ? ColumnMeta.ValueTypeEnum.fromValue(co.getValueType())
                  : c.getValueType())
          .visible(co.getVisible() != null ? co.getVisible() : c.getVisible())
          .sortable(co.getSortable() != null ? co.getSortable() : c.getSortable()));
    }
    return out;
  }

  private List<ColumnMeta> applyColumnIncludes(List<ColumnMeta> cols, PageSchemaOverride override) {
    if (override.getIncludeColumns().isEmpty()) {
      return cols;
    }
    return cols.stream().filter(c -> override.getIncludeColumns().contains(c.getKey())).toList();
  }

  private List<ColumnMeta> applyColumnOrder(List<ColumnMeta> cols, PageSchemaOverride override) {
    if (override.getColumnOrder().isEmpty()) {
      return cols;
    }
    Map<String, Integer> idx = new LinkedHashMap<>();
    for (int i = 0; i < override.getColumnOrder().size(); i++) {
      idx.put(override.getColumnOrder().get(i), i);
    }
    return cols.stream()
        .sorted(Comparator.comparingInt(c -> idx.getOrDefault(c.getKey(), Integer.MAX_VALUE)))
        .toList();
  }

  private boolean isIdField(Field<?> f) {
    String n = f.getName().toLowerCase();
    Class<?> t = f.getType();
    return (n.equals("id") || n.endsWith("_id")) && (Long.class.equals(t) || long.class.equals(t)
        || Integer.class.equals(t) || int.class.equals(t));
  }

  private boolean isEnumCandidate(Field<?> f) {
    String n = f.getName().toLowerCase();
    return n.equals("status") || n.equals("source") || n.endsWith("_type") || n.endsWith(
        "_dimension");
  }

  private boolean isStringField(Field<?> f) {
    return String.class.equals(f.getType());
  }

  private boolean isSortable(Field<?> f) {
    Class<?> t = f.getType();
    return String.class.equals(t) || Number.class.isAssignableFrom(t) || Boolean.class.equals(t)
        || boolean.class.equals(t) || OffsetDateTime.class.equals(t) || LocalDateTime.class.equals(
        t)
        || LocalDate.class.equals(t);
  }

  private ColumnMeta.ValueTypeEnum inferValueType(Field<?> f) {
    String n = f.getName().toLowerCase();
    Class<?> t = f.getType();
    if (n.equals("status") || n.equals("source") || n.endsWith("_type") || n.endsWith(
        "_dimension")) {
      return ColumnMeta.ValueTypeEnum.ENUM;
    }
    if (Long.class.equals(t) || long.class.equals(t) || Integer.class.equals(t) || int.class.equals(
        t)) {
      return ColumnMeta.ValueTypeEnum.LONG;
    }
    if (OffsetDateTime.class.equals(t) || LocalDateTime.class.equals(t) || LocalDate.class.equals(
        t)) {
      return ColumnMeta.ValueTypeEnum.DATETIME;
    }
    if (Boolean.class.equals(t) || boolean.class.equals(t)) {
      return ColumnMeta.ValueTypeEnum.BOOLEAN;
    }
    return ColumnMeta.ValueTypeEnum.STRING;
  }

  private String toCamelCase(String value) {
    String[] parts = value.split("_");
    StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
    for (int i = 1; i < parts.length; i++) {
      String p = parts[i].toLowerCase();
      sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
    }
    return sb.toString();
  }

  private String defaultLabel(String key) {
    return key;
  }

  private record Descriptor(Field<?> field, String originalKey, String key, String label) {

  }
}
