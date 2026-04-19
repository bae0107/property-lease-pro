package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.water;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class YunDingWMeterCheckResponse extends YunDingError {

  private Info info;

  public YunDingWMeterCheckResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }

  @Getter
  @Setter
  @ToString
  @NoArgsConstructor
  public static class Info {

    @SerializedName("sn")
    private String collectorId;

    private double amount;

    @SerializedName("amount_time")
    private String recordTime;

    // 设备本身在线状态
    @SerializedName("onoff")
    private int onOff;

    @SerializedName("onoff_time")
    private String onOffTime;

    // 1冷水，2热水
    @SerializedName("meter_type")
    private int meterType;

    // 网管在线状态
    @SerializedName("onoff_line")
    private int onOffLine;
  }
}
