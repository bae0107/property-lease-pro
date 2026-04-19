package com.jugu.propertylease.device.common.entity.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "表类基础请求")
public class MeterRequest {

  @Schema(description = "业务内表主键id")
  private long id;

  @Schema(description = "操作（1为开，2为关等等一系列操作")
  private int serviceType;

  @Schema(description = "开始时间，秒级时间戳")
  private long startTime;

  @Schema(description = "结束时间，秒级时间戳")
  private long endTime;
}
