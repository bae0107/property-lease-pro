package com.jugu.propertylease.device.common.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(description = "设备事件通知")
public class EventInfoDTO {

  @Schema(description = "服务主键（业务内部）")
  private long id;

  @Schema(description = "设备ID主键")
  private long deviceKey;

  @Schema(description = "服务商识别1为云丁2为合一")
  private int providerOp;

  @Schema(description = "1为电2为水3为锁")
  private int deviceType;

  @Schema(description = "事件名称")
  private String eventName;

  @Schema(description = "事件描述")
  private String eventInfo;

  @Schema(description = "是否已读，1为未读，2为已读")
  private int hasRead;

  @Schema(description = "通知时间")
  private String eventTime;
}
