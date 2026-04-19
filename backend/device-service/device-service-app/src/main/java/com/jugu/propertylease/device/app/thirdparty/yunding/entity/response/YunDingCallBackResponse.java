package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.common.utils.Common;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YunDingCallBackResponse extends YunDingError {

  @SerializedName(value = "serviceid", alternate = {"service_id"})
  private String serviceId;

  public YunDingCallBackResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }

  public boolean isValid() {
    return !Common.isStringInValid(serviceId);
  }
}
