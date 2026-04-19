package com.jugu.propertylease.device.app.thirdparty.yunding.entity;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class YunDingAccountDTO {

  @SerializedName("client_id")
  private String clientId;

  @SerializedName("client_secret")
  private String clientSecret;
}
