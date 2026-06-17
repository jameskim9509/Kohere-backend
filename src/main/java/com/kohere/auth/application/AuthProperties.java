package com.kohere.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 정책값(refresh). refresh 만료(=Redis TTL 기준, ADR-0011)와 해시 pepper(SHA-256+pepper, ADR-0006)를 담는다.
 * 운영 pepper는 환경변수/Secrets Manager로 주입한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

  private long refreshTtlSeconds = 1209600;
  private String refreshPepper;
}
