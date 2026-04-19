package com.jugu.propertylease.device.common.entity.dto;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "第三方服务回调确认，请先读取isDelay确认任务状态，false时其他参数无需使用")
@NoArgsConstructor
public class ServiceRecordDTO {

  @Schema(description = "回调服务IdKey（后台使用）")
  private long serviceKey;

  @Schema(description = "第三方服务商op值")
  private int providerOp;

  @Schema(description = "第三方内部id（第三方接口查询用）")
  private String deviceId;

  @Schema(description = "服务状态，true说明结果延迟，需要依赖回调接口确认，false说明结果已经生效，无需确认")
  @SerializedName("isDelay")
  private boolean isDelay;

  public ServiceRecordDTO(long serviceKey, int providerOp, String deviceId, boolean isDelay) {
    this.serviceKey = serviceKey;
    this.providerOp = providerOp;
    this.deviceId = deviceId;
    this.isDelay = isDelay;
  }
}
