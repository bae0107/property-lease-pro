package com.jugu.propertylease.common.info;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@Schema(description = "请求数据结构")
public class RequestDataInfo<T> extends RequestInfo {

  @Schema(description = "请求数据")
  private T data;

  public RequestDataInfo(String userId, String userName, T data) {
    super(userId, userName);
    this.data = data;
  }
}
