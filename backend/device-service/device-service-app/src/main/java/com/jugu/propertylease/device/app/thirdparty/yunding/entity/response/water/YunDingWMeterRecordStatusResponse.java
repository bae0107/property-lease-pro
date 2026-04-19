package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.water;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class YunDingWMeterRecordStatusResponse extends YunDingError {

  @SerializedName("reading")
  private int status;

  public YunDingWMeterRecordStatusResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }
}
