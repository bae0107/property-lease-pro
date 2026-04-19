package com.jugu.propertylease.device.app.thirdparty.yunding.entity.request;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YunDingLockPwdOpRequest extends YunDingRequest {

  @SerializedName("password_id")
  private int passwordId;

  public YunDingLockPwdOpRequest(int passwordId, String deviceId, String token) {
    super(deviceId, token);
    this.passwordId = passwordId;
  }
}
