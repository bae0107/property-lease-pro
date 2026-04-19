package com.jugu.propertylease.device.app.thirdparty.yunding;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class YunDingCallbackDTO {

  String service;

  @SerializedName("serviceid")
  String serviceId;

  String uuid;

  String result;

  String sign;

  @SerializedName("time")
  long time;
}
