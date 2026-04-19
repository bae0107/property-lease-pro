package com.jugu.propertylease.device.app.thirdparty.hans;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(prefix = "hs")
@PropertySource(value = "${meter.config.path}")
@Data
public class HansAccount {

  private static HansAccount instance;
  private String username;
  private String password;
  private int typeStatus;
  private String domain;

  public static HansAccount getInstance() {
    return instance;
  }

  @PostConstruct
  public void init() {
    instance = this;
  }

  public HansAccountDTO toDTO() {
    HansAccountDTO dto = new HansAccountDTO();
    dto.setUsername(username);
    dto.setPassword(password);
    dto.setTypeStatus(typeStatus);
    return dto;
  }
}
