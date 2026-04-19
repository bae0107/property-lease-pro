package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.notify;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@NoArgsConstructor
public class YunDingAddPwdNotify {

  @SerializedName("id")
  private int pwdId;

  @SerializedName("ErrNo")
  private int errorNo;
}
