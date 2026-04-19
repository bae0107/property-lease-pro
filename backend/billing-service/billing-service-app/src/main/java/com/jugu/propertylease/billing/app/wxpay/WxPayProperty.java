package com.jugu.propertylease.billing.app.wxpay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "${wx.config.path}", encoding = "UTF-8")
@ConfigurationProperties(prefix = "wx")
@Data
public class WxPayProperty {

  private String hostAppId;

  private String userAppId;

  private String mchId;

  private String apiV3Key;

  private String notifyUrl;

  private String privateCert;

  private String privateKey;
}
