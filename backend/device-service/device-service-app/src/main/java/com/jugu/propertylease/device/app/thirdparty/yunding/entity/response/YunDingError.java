package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class YunDingError {

  @SerializedName("ErrNo")
  private int errCode;

  @SerializedName("ErrMsg")
  private String errMsg;

  public YunDingError(int errCode, String errMsg) {
    this.errCode = errCode;
    this.errMsg = errMsg;
  }
}
