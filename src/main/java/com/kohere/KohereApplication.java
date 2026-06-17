package com.kohere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KohereApplication {

  public static void main(String[] args) {
    SpringApplication.run(KohereApplication.class, args);
  }
}
