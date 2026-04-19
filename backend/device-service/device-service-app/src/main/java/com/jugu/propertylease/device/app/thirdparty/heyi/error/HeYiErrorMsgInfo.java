package com.jugu.propertylease.device.app.thirdparty.heyi.error;

import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiResponse;
import lombok.Getter;

@Getter
public enum HeYiErrorMsgInfo {
  DEFAULT(-999, "发生未知错误，未知错误"),
  SUCCESS(1, "成功");

  private final int errCode;

  private final String errMsg;

  HeYiErrorMsgInfo(int errCode, String errMsg) {
    this.errCode = errCode;
    this.errMsg = errMsg;
  }

  public static HeYiErrorMsgInfo findMsgInfo(HeYiResponse<?> heYiResponse) {
    int errCode = heYiResponse.getStatus();
    return findMsgInfo(errCode);
  }

  public static HeYiErrorMsgInfo findMsgInfo(int errorCode) {
    for (HeYiErrorMsgInfo msgInfo : values()) {
      if (msgInfo.getErrCode() == errorCode) {
        return msgInfo;
      }
    }
    return HeYiErrorMsgInfo.DEFAULT;
  }
}
