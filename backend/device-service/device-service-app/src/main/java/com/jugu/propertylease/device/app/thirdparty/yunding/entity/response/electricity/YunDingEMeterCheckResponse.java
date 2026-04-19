package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.electricity;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YunDingEMeterCheckResponse extends YunDingError {

  @SerializedName("consume_amount")
  private float consumeAmount;

  @SerializedName("consume_amount_time")
  private long recordTime;

  /**
   * 1为合闸状态，2为跳闸状态，-1为未知
   */
  @SerializedName("enable_state")
  private int enableState;

  @SerializedName("enable_state_time")
  private long enableStateTime;

  /**
   * 是否在线状态1为在线（子表看）
   */
  @SerializedName("onoff_line")
  private int onOffLine;

  /**
   * 是否在线状态更新时间MS
   */
  @SerializedName("onoff_time")
  private long onOffTime;

  /**
   * 通信状态1为正常（采集器看）
   */
  @SerializedName("trans_status")
  private int transStatus;

  /**
   * 通信状态刷新时间MS
   */
  @SerializedName("trans_status_time")
  private long transStatusTime;


  public YunDingEMeterCheckResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }
}
