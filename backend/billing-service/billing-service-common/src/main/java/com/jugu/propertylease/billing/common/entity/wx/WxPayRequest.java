package com.jugu.propertylease.billing.common.entity.wx;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "微信支付发起请求")
public class WxPayRequest {

  @Schema(description = "业务生成的账单号")
  private String billingId;

  @Schema(description = "微信内部id(app支付时必传)")
  private String openId;

  @Schema(description = "用户端ip(扫码支付,H5支付时必传)")
  private String ip;

  @Schema(description = "管理员发起:1,租户发起:2")
  private int rootIndex;

  @Schema(description = "支付类型方式")
  private WxPayType payType;
}
