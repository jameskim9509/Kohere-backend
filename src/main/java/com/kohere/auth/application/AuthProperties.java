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

  /**
   * 임대인 웹 로그인 정책(app.auth.web, ADR-0047). 웹 refresh TTL은 앱과 같은 14일이라 별도 키를 두지 않는다.
   *
   * <p>같은 트리의 {@code app.auth.web.refresh-cookie.*}는 여기가 아니라 {@link
   * com.kohere.common.security.RefreshCookieProperties}가 바인딩한다 — 쿠키는 도메인 정책이 아니라 HTTP 전송 수단이라 공유
   * 커널의 보안 패키지에 있다. 이 클래스는 알 필요가 없어 필드를 두지 않으며, 미지정 필드는 무시되므로 바인딩이 충돌하지 않는다.
   */
  @Getter
  @Setter
  public static class Web {

    /** 비밀번호 연속 실패 상한 — 도달하면 계정을 잠근다. 시간 경과 자동 해제도 해제 API도 없다(US-1-12). */
    private int loginMaxFailedAttempts = 10;

    /** 로그인 시도 레이트리밋 한도. */
    private Login login = new Login();

    /**
     * 웹 로그인 시도 한도(app.auth.web.login). 위 {@code loginMaxFailedAttempts}가 <b>한 계정을 잠그는</b> 임계값인 것과
     * 달리 이쪽은 <b>요청 자체를 받아 주는</b> 임계값이라 축(IP·이메일)도 창(1시간)도 다르다.
     */
    @Getter
    @Setter
    public static class Login {

      /**
       * 같은 IP에서 1시간에 허용하는 로그인 시도(초과 시 429). 이메일 한도보다 넉넉한 것은 <b>공유 IP</b>(사무실·모바일 NAT) 때문이다 — 한 사무실의
       * 임대인 여럿이 같은 출구 IP를 쓰는 정상 상황을 막지 않을 만큼은 남겨 둔다. 위조 가능한 축이라 정밀도보다 비용 상한이 목적이다.
       */
      private int ipMaxPerHour = 60;

      /**
       * 같은 이메일로 1시간에 허용하는 로그인 시도(초과 시 429). <b>잠금 임계값의 2배</b>로 잡는다 — 정상 사용자가 몇 번 틀리고 다시 맞히는 흐름을 막지
       * 않으면서, 시간당 잠금 가능 계정 수를 실질적으로 묶는다.
       *
       * <p><b>이 값이 잠금 임계값 이하로 내려가면 잠금이 사실상 도달 불가능해진다</b> — 한 시간 창의 이메일 예산을 정확히 전부 써야 상한에 닿고, 그 창에
       * 성공 로그인이 하나라도 섞이면(레이트리밋은 성공·실패를 가리지 않는다) 그 창에서는 잠글 수 없다. 두 값은 함께 움직인다.
       */
      private int emailMaxPerHour = 20;
    }
  }
}
