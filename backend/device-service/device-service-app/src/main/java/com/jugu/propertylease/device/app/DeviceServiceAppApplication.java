package com.jugu.propertylease.device.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAsync
@SpringBootApplication
@ConfigurationPropertiesScan("com.jugu.propertylease.device.app")
public class DeviceServiceAppApplication {

  public static void main(String[] args) {
    SpringApplication.run(DeviceServiceAppApplication.class, args);
  }
}
