package com.jugu.propertylease.billing.common.entity.wx;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@Schema(description = "微信支付拉起参数")
public class WxPayResponse {

  @Schema(description = "微信小程序支付拉起参数")
  private AppResponse appResponse;

  @Schema(description = "微信H5网页支付拉起参数")
  private WebResponse webResponse;

  @Schema(description = "微信扫码支付拉起参数")
  private QRResponse qrResponse;

  @Getter
  public enum WxPayStatus {
    SUCCESS,
    PAYERROR,
    USERPAYING,
    REFUND,
    NOTPAY,
    CLOSED,
    REVOKED,
    UNKNOWN;

    public static WxPayStatus findStatusByResult(String result) {
      for (WxPayStatus status : WxPayStatus.values()) {
        if (result.trim().equalsIgnoreCase(status.name().trim())) {
          return status;
        }
      }
      return UNKNOWN;
    }
  }

  @Getter
  @Setter
  @ToString
  public static class AppResponse {

    private String appId;

    private String timeStamp;

    private String randomStr;

    private String prepayId;

    private String signType;

    private String paySign;

    private String expireTime;
  }

  @Getter
  @Setter
  @ToString
  public static class WebResponse {

    private String payUrl;

    private String expireTime;
  }

  @Getter
  @Setter
  @ToString
  public static class QRResponse {

    private String qrStr;

    private String expireTime;
  }
}
