package com.jugu.propertylease.device.app.thirdparty.yunding.entity;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;


@Component
@Configuration
@ConfigurationProperties(prefix = "yd")
@PropertySource(value = "${meter.config.path}")
@Data
public class YunDingAccount {

  private static YunDingAccount instance;
  private String domain;
  private String clientId;
  private String clientSecret;

  public static YunDingAccount getInstance() {
    return instance;
  }

  @PostConstruct
  public void init() {
    instance = this;
  }

  public YunDingAccountDTO toDTO() {
    YunDingAccountDTO dto = new YunDingAccountDTO();
    dto.setClientId(this.clientId);
    dto.setClientSecret(this.clientSecret);
    return dto;
  }
}
