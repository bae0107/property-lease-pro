package com.jugu.propertylease.device.app.entity.response.water;

import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "水表数据查询response结构")
public class WMeterCheckResponse extends DeviceResponse {

  @Schema(description = "水表当前读数")
  private double consumeAmount;

  /**
   * 日期秒级维度 年月日时分秒
   */
  @Schema(description = "水表当前读数的抄表时间")
  private String recordTime;

  @Schema(description = "水表在线状态：1在线，2离线，-1初次绑定（云丁）")
  private int onOff;

  /**
   * 是否在线状态更新时间
   */
  @Schema(description = "水表是否在线状态刷新时间")
  private String onOffTime;

  @Schema(description = "水表冷热水：1冷水，2热水")
  private int meterType;

  /**
   * 网关是否在线状态
   */
  @Schema(description = "网关是否在线状态：1在线,2离线,-1为未知")
  private int onOffLine;

  public void successFullInfoResponse(double consumeAmount, String recordTime, int onOff,
      String onOffTime, int meterType,
      int onOffLine) {
    super.setSuccess(true);
    this.consumeAmount = consumeAmount;
    this.recordTime = recordTime;
    this.onOff = onOff;
    this.onOffTime = onOffTime;
    this.meterType = meterType;
    this.onOffLine = onOffLine;
  }
}
