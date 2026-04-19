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
public class YunDingEventDTO {

  private String event;

  private long time;

  private String uuid;

  @SerializedName("home_id")
  private String homeId;

  @SerializedName("room_id")
  private String roomId;

  @SerializedName("nickname")
  private String nickName;

  private String detail;

  private String sign;
}
