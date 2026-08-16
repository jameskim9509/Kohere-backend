package com.kohere.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 정책값(refresh·이메일·연락처 인증·임대인 웹). refresh 만료(=Redis TTL 기준, ADR-0011)와 해시 pepper(SHA-256+pepper,
 * ADR-0006), 이메일·연락처 인증번호 해시 pepper를 담는다. 운영 pepper는 환경변수/Secrets Manager로 주입한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

  private long refreshTtlSeconds = 1209600;
  private String refreshPepper;
  private String emailPepper;
  private String phonePepper;

  /** 임대인 웹(로컬 자격증명) 정책값. */
  private Web web = new Web();

  /** 임대인 웹 로그인 정책(app.auth.web, ADR-0047). 웹 refresh TTL은 앱과 같은 14일이라 별도 키를 두지 않는다. */
  @Getter
  @Setter
  public static class Web {

    /** 비밀번호 연속 실패 상한 — 도달하면 계정을 잠근다. 시간 경과 자동 해제도 해제 API도 없다(US-1-12). */
    private int loginMaxFailedAttempts = 5;
  }
}
