package com.jugu.propertylease.billing.common.entity.wx;

import com.jugu.propertylease.common.utils.Common;
import lombok.Getter;

@Getter
public enum WxPayType {
  APP(wxPayRequest -> checkCommon(wxPayRequest) && !Common.isStringInValid(
      wxPayRequest.getOpenId())),
  H5(wxPayRequest -> checkCommon(wxPayRequest) && !Common.isStringInValid(wxPayRequest.getIp())),
  QR(wxPayRequest -> checkCommon(wxPayRequest) && !Common.isStringInValid(wxPayRequest.getIp()));

  private final Checker checker;

  WxPayType(Checker checker) {
    this.checker = checker;
  }

  public static boolean checkCommon(WxPayRequest wxPayRequest) {
    int rootIndex = wxPayRequest.getRootIndex();
    return !Common.isStringInValid(wxPayRequest.getBillingId()) && isRootLegal(rootIndex);
  }

  public static boolean isRootLegal(int rootIndex) {
    return rootIndex == 1 || rootIndex == 2;
  }

  public interface Checker {

    boolean isValid(WxPayRequest wxPayRequest);
  }
}
