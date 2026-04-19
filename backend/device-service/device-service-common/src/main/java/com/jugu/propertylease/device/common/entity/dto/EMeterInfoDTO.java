package com.jugu.propertylease.device.common.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "电表类数据结构")
public class EMeterInfoDTO extends MeterInfoDTO {

  @Schema(description = "电表闸门状态（1为关闭、2为打开、3为关闭中、4为打开中、-1为异常）")
  private int switchStatus;

  @Schema(description = "闸门当前状态的修改时间（第三方提供）")
  private String enableStateTime;

  @Schema(description = "最后通讯时间，合一没有")
  private String lastCommTime;
}
