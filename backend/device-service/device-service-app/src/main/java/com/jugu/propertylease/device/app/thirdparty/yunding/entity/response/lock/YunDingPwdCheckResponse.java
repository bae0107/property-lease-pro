package com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.lock;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class YunDingPwdCheckResponse extends YunDingError {

  @SerializedName("passwords")
  private Map<String, YunDingPwdInfo> yunDingPwdInfos;

  public YunDingPwdCheckResponse(int errCode, String errMsg) {
    super(errCode, errMsg);
  }
}
