package com.jugu.propertylease.device.app.thirdparty.heyi.entity;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(prefix = "hy")
@PropertySource(value = "${meter.config.path}")
@Data
public class HeYiAccount {

  private static HeYiAccount instance;
  private String domain;
  private String clientId;
  private String clientSecret;
  private String grantType;

  public static HeYiAccount getInstance() {
    return instance;
  }

  @PostConstruct
  public void init() {
    instance = this;
  }

  public HeYiAccountDTO toDTO() {
    HeYiAccountDTO dto = new HeYiAccountDTO();
    dto.setClientId(this.clientId);
    dto.setClientSecret(this.clientSecret);
    dto.setGrantType(this.grantType);
    return dto;
  }
}
