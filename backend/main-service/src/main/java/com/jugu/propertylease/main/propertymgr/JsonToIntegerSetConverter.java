package com.jugu.propertylease.main.propertymgr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jooq.Converter;
import org.jooq.JSON;

public class JsonToIntegerSetConverter implements Converter<JSON, Set<Integer>> {

  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Set<Integer> from(JSON json) {
    if (json == null) {
      return null;
    }
    try {
      // 将 JSON 字符串转为 Set<Integer>
      return mapper.readValue(json.data(), new TypeReference<>() {
      });
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JSON to Set<Integer>", e);
    }
  }

  @Override
  public JSON to(Set<Integer> integers) {
    if (integers == null) {
      return null;
    }
    try {
      // 将 Set<Integer> 转为 JSON
      return JSON.json(mapper.writeValueAsString(integers));
    } catch (Exception e) {
      throw new RuntimeException("Failed to convert Set<Integer> to JSON", e);
    }
  }

  @Override
  public @NotNull Class<JSON> fromType() {
    return JSON.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull Class<Set<Integer>> toType() {
    return (Class<Set<Integer>>) (Class<?>) Set.class;
  }
}
