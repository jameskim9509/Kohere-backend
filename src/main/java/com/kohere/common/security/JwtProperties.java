package com.kohere.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서버 JWT(access/온보딩) 정책값. HS256 시크릿과 만료 시간을 담는다(ADR-0009/0011).
 *
 * <p>시크릿은 코드에 박지 않고 런타임 주입한다(env/Secrets Manager). 최소 256bit(32byte) 이상이어야 한다(ADR-0011 D5).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

  private String secret;
  private String issuer = "kohere";
  private long accessTtlSeconds = 3600;
  private long onboardingTtlSeconds = 1800;
}
