package com.jugu.propertylease.main.outer.wx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "${wx.config.path}", encoding = "UTF-8")
@ConfigurationProperties(prefix = "wx")
@Data
public class WxOpenConfig {

  private String appId;

  private String appSecret;

  private String notifyUrl;
}
