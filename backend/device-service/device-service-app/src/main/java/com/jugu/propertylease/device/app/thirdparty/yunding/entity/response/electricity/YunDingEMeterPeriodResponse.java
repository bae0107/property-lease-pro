package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.electricity;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YunDingEMeterPeriodResponse extends YunDingError {

  @SerializedName("consumption")
  private float consumption;

  @SerializedName("start_time")
  private int startTime;

  @SerializedName("end_time")
  private int endTime;

  public YunDingEMeterPeriodResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }
}
