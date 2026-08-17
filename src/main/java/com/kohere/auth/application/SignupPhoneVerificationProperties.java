package com.kohere.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가입용(비로그인) 연락처 SMS 인증 전용 정책값 — <b>레이트리밋 한도만</b> 둔다(#229 D6). 인증번호 자릿수·코드 TTL·검증 마커 TTL·시도 상한·재발송
 * 간격은 온보딩용과 같은 정책이라 {@link PhoneVerificationProperties}({@code app.phone.*})를 그대로 재사용한다 — 같은 값을 두 벌
 * 두면 한쪽만 바뀌어 발송은 5분, 확인은 3분 같은 조합이 조용히 생긴다.
 *
 * <p>여기 두 값만 별도인 이유는 <b>이 경로에만 있는 위협</b>이기 때문이다 — 온보딩용은 인증된 {@code userId}로 묶이지만 가입용은 permitAll이라
 * 번호·IP 단위 한도가 유일한 남용 방지책이다(문자 폭탄·발송비).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth.signup-phone")
public class SignupPhoneVerificationProperties {

  /** 같은 번호로 1시간에 허용하는 발송 시도(초과 시 429). */
  private int phoneMaxPerHour = 5;

  /** 같은 IP에서 1시간에 허용하는 발송 시도(초과 시 429) — 번호를 바꿔가며 태우는 남용을 막는다. */
  private int ipMaxPerHour = 20;
}
