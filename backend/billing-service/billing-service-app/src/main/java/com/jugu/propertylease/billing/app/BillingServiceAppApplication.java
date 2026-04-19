package com.jugu.propertylease.billing.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan("com.jugu.propertylease.billing.app")
@EnableFeignClients(basePackages = "com.jugu.propertylease.main.client.api")
public class BillingServiceAppApplication {

  public static void main(String[] args) {
    SpringApplication.run(BillingServiceAppApplication.class, args);
  }
}
