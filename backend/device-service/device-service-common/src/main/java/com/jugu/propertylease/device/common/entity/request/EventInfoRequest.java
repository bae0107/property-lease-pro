package com.jugu.propertylease.device.common.entity.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "事件消息查询请求")
public class EventInfoRequest {

  @Schema(description = "一批的数量")
  private int batchSize;

  @Schema(description = "跳过的条数")
  private int passNum;

  @Schema(description = "查询已读还是未读消息")
  private int hasRead;
}
