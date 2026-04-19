package com.jugu.propertylease.main.outer.ali;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "${ali.config.path}", encoding = "UTF-8")
@ConfigurationProperties(prefix = "ali")
@Data
public class AliYunConfig {

  private String id;

  private String key;

  private String sign;

  private String region;

  private String url;
}
