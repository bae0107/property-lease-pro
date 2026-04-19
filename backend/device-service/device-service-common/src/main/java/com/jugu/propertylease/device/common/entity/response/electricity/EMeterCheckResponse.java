package com.jugu.propertylease.device.common.entity.response.electricity;

import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "电表数据查询response结构")
public class EMeterCheckResponse extends DeviceResponse {

  @Schema(description = "电表当前读数")
  private float consumeAmount;

  /**
   * 日期秒级维度 年月日时分秒
   */
  @Schema(description = "电表当前读数的抄表时间")
  private String recordTime;

  @Schema(description = "电表当前的闸门状态，1为打开2位关闭")
  private int enableState;

  /**
   * 日期秒级维度
   */
  @Schema(description = "电表当前的闸门状态的操作时间")
  private String enableStateTime;

  /**
   * 是否在线状态1为在线（子表看）
   */
  @Schema(description = "电表是否在线状态（子表看）")
  private int onOffLine;

  /**
   * 是否在线状态更新时间
   */
  @Schema(description = "电表是否在线状态（子表看）刷新时间")
  private String onOffTime;

  /**
   * 通信状态1为正常（采集器看）
   */
  @Schema(description = "电表是否在线状态（母表采集器看）")
  private int transStatus;

  /**
   * 通信状态刷新时间MS
   */
  @Schema(description = "电表是否在线状态（母表采集器看）刷新时间")
  private String transStatusTime;

  public void successReadConsumeResponse(float consumeAmount, String recordTime) {
    super.setSuccess(true);
    this.consumeAmount = consumeAmount;
    this.recordTime = recordTime;
  }

  public void successCheckEnableResponse(int enableState, String enableStateTime) {
    super.setSuccess(true);
    this.enableState = enableState;
    this.enableStateTime = enableStateTime;
  }

  public void successFullInfoResponse(float consumeAmount, String recordTime, int enableState,
      String enableStateTime,
      int onOffLine, String onOffTime, int transStatus, String transStatusTime) {
    super.setSuccess(true);
    this.consumeAmount = consumeAmount;
    this.recordTime = recordTime;
    this.enableState = enableState;
    this.enableStateTime = enableStateTime;
    this.onOffLine = onOffLine;
    this.onOffTime = onOffTime;
    this.transStatus = transStatus;
    this.transStatusTime = transStatusTime;
  }
}
