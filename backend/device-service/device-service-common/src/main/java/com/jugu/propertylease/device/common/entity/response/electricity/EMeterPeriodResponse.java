package com.jugu.propertylease.device.common.entity.response.electricity;

import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EMeterPeriodResponse extends DeviceResponse {

  @Schema(description = "度数消耗量")
  private float consumption;

  /**
   * 日期秒级维度
   */
  @Schema(description = " 消耗量开始时间")
  private String startTime;

  /**
   * 日期秒级维度
   */
  @Schema(description = " 消耗量结束时间")
  private String endTime;

  public void successPeriodResponse(float consumption, String startTime, String endTime) {
    super.setSuccess(true);
    this.consumption = consumption;
    this.startTime = startTime;
    this.endTime = endTime;
  }
}
