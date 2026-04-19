package com.jugu.propertylease.billing.app.wxpay;

import lombok.Data;

@Data
public class WxPayDes {

  private String des;

  private String orderId;

  private int amountCent;

  private String openId;

  private String ip;

  private String expireTime;
}
