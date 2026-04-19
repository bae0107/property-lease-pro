package com.jugu.propertylease.common.jooq;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.impl.AbstractConverter;

public class ShanghaiOffsetDateTimeConverter extends
    AbstractConverter<LocalDateTime, OffsetDateTime> {

  public ShanghaiOffsetDateTimeConverter() {
    super(LocalDateTime.class, OffsetDateTime.class);
  }

  @Override
  public OffsetDateTime from(LocalDateTime databaseObject) {
    // 从数据库读取无时区时刻，贴上 +8 时区标签
    return databaseObject == null ? null : databaseObject.atOffset(ZoneOffset.ofHours(8));
  }

  @Override
  public LocalDateTime to(OffsetDateTime userObject) {
    // 写入数据库前，先对齐到 +8 时区时刻，再取其数值部分
    return userObject == null ? null :
        userObject.withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDateTime();
  }
}
