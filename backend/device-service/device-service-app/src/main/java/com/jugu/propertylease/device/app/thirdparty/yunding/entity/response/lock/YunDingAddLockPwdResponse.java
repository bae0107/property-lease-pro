package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingCallBackResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class YunDingAddLockPwdResponse extends YunDingCallBackResponse {

  @SerializedName("id")
  private int passwordId;

  public YunDingAddLockPwdResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }
}
