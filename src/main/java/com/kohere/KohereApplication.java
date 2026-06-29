package com.kohere;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @EnableMongock — MongoDB 1회성 마이그레이션(@ChangeUnit) 러너 활성화(ADR-0032). 스캔 패키지·비활성은 application*.yml.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMongock
public class KohereApplication {

  public static void main(String[] args) {
    SpringApplication.run(KohereApplication.class, args);
  }
}
