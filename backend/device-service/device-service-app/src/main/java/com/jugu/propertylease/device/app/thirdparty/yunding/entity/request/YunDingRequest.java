package com.jugu.propertylease.device.app.thirdparty.yunding.entity.request;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class YunDingRequest {

  @SerializedName("uuid")
  private String deviceId;

  @SerializedName("access_token")
  private String token;

  public YunDingRequest(String deviceId, String token) {
    this.deviceId = deviceId;
    this.token = token;
  }
}
