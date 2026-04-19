package com.jugu.propertylease.device.common.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "水表类数据结构")
public class WMeterInfoDTO extends MeterInfoDTO {

  private int meterType;

  private int gateWayStatus;
}
