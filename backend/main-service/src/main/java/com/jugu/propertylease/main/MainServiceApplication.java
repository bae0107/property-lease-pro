package com.jugu.propertylease.main;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan({"com.jugu.propertylease.main", "com.jugu.propertylease.common"})
@ConfigurationPropertiesScan({"com.jugu.propertylease.main", "com.jugu.propertylease.common"})
public class MainServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MainServiceApplication.class, args);
  }

}
