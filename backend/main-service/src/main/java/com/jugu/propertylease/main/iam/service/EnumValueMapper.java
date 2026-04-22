package com.jugu.propertylease.main.iam.service;

import java.util.function.Function;

/**
 * OpenAPI 生成枚举的安全映射工具。
 */
public final class EnumValueMapper {

  private EnumValueMapper() {
  }

  public static <T> T nullableFromValue(String value, Function<String, T> mapper) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return mapper.apply(value);
  }
}
