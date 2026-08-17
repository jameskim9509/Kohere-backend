package com.kohere.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 refresh 토큰 쿠키의 속성값(ADR-0048 §2). 이름·경로·{@code SameSite}·{@code Secure} 넷만 담는다.
 *
 * <p><b>만료(Max-Age)는 여기 없다</b> — 웹 refresh TTL은 앱과 같은 14일이라 {@code app.auth.refresh-ttl-seconds}를
 * 그대로 따라간다(ADR-0048 §4). 쿠키 전용 TTL 키를 만들면 "쿠키 {@code Max-Age}와 서버 TTL 중 무엇이 진짜 만료인가"가 두 벌이 되어 어긋날
 * 자리가 생긴다.
 *
 * <p><b>{@code secure}의 기본값은 {@code true}이고 {@code application-local.yml}에서만 {@code false}로 내린다</b>
 * — 반대로 두면 운영에 {@code false}가 새어 나갈 수 있다. 로컬은 평문 http라 {@code Secure} 쿠키를 브라우저가 저장하지 않아 재발급이 조용히
 * 깨진다(서버 로그에는 "refresh 없음"으로만 남는다).
 *
 * <p><b>왜 {@code auth}가 아니라 {@code common}인가</b> — 쿠키는 도메인 규칙이 아니라 HTTP 전송 수단이라 {@link
 * JwtProperties}·{@link JwtTokenService}와 같은 자리(공유 커널의 보안 패키지)에 둔다. 설정 키만 {@code app.auth.*} 아래에 있는
 * 것은 ADR-0048이 고정한 계약이며, {@code app.auth}를 바인딩하는 {@code AuthProperties}와는 서로 다른 하위 트리를 읽으므로 충돌하지
 * 않는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth.web.refresh-cookie")
public class RefreshCookieProperties {

  /** 쿠키 이름. 요청 본문의 필드명과 같게 두어 재발급·로그아웃이 쿠키·본문 어느 쪽에서 읽든 같은 값이 되게 한다. */
  private String name = "refreshToken";

  /** 쿠키를 실어 보낼 경로. 좁힐수록 refresh가 노출되는 요청이 줄어든다(재발급·로그아웃 경로에만 실린다). */
  private String path = "/api/v1/auth";

  /** 크로스사이트 요청에 쿠키가 실리지 않게 한다 — 별도 CSRF 토큰 없이 {@code csrf.disable()}을 유지하는 근거다. */
  private String sameSite = "Lax";

  /** 평문 구간으로 새지 않게 한다. 로컬(http) 프로파일에서만 끈다. */
  private boolean secure = true;
}
