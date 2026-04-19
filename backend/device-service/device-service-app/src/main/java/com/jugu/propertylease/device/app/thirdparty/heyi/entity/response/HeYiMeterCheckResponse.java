package com.jugu.propertylease.device.app.thirdparty.heyi.entity.response;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.common.utils.Common;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class HeYiMeterCheckResponse {

  /**
   * 消息类型（手动/自动）
   */
  @SerializedName("type")
  private String type;

  /**
   * success/failed
   */
  @SerializedName(value = "result", alternate = {"read_status"})
  private String result;

  /**
   * 1：手动抄表
   */
  @SerializedName("isAutoClear")
  private String isAutoClear;

  @SerializedName(value = "meterNo", alternate = {"device_no"})
  private String meterNo;

  /**
   * 10:冷水表 11:热水表 20:热量表 22:冷热表 30:气表 40:电表
   */
  @SerializedName(value = "meterType", alternate = {"device_type"})
  private int meterType;

  @SerializedName("data")
  private double consumeAmount;

  @SerializedName("datetime")
  private String recordTime;

  /**
   * 阀门状态 close: 关闭 open: 开启 noValveCap: 无阀门功能
   */
  @SerializedName("valve_status")
  private String valveStatus;

  /**
   * 开关闸时间（猜测）
   */
  @SerializedName("valve_time")
  private String valveTime;

  /**
   * 设备状态 offline:离线 online: 在线
   */
  private String status;

  public boolean isSuccess() {
    return !Common.isStringInValid(this.result) && "success".equals(
        this.result.toLowerCase(Locale.ROOT).trim());
  }

  public int convertStatus() {
    int onOffLine;
    if ("online".equals(status)) {
      onOffLine = 1;
    } else if ("offline".equals(status)) {
      onOffLine = 2;
    } else {
      onOffLine = -1;
    }
    return onOffLine;
  }
}
