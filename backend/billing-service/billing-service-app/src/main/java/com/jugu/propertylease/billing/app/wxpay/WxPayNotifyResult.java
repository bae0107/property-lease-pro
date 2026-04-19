package com.jugu.propertylease.billing.app.wxpay;

import com.google.gson.annotations.SerializedName;
import com.jugu.propertylease.common.utils.GsonFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class WxPayNotifyResult {

  private static final String FAIL = "FAIL";

  private static final String SUCCESS = "SUCCESS";

  private static final String CODE = "code";

  private static final String MSG = "message";

  private static final String S_M = "成功";

  @SerializedName("code")
  private String code;

  @SerializedName("message")
  private String message;

  public static String error(String errorMsg) {
    WxPayNotifyResult result = new WxPayNotifyResult();
    result.setCode(FAIL);
    result.setMessage(errorMsg);
    return GsonFactory.toJson(result);
  }

  public static String success() {
    WxPayNotifyResult result = new WxPayNotifyResult();
    result.setCode(SUCCESS);
    result.setMessage(S_M);
    return GsonFactory.toJson(result);
  }
}
