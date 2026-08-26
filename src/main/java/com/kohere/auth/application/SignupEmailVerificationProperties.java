package com.kohere.auth.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가입용(비로그인) 이메일 인증 전용 정책값 — <b>레이트리밋 한도만</b> 둔다. 인증번호 자릿수·코드 TTL·검증 마커 TTL·시도 상한·재발송 간격은 정식 사용자용과
 * 같은 정책이라 {@link EmailVerificationProperties}({@code app.email.*})를 그대로 재사용한다 — 같은 값을 두 벌 두면 한쪽만
 * 바뀌어 발송은 5분, 확인은 3분 같은 조합이 조용히 생긴다({@link SignupPhoneVerificationProperties}가 {@code app.phone.*}에
 * 대해 선 것과 같은 구조다).
 *
 * <p>여기 두 값만 별도인 이유는 <b>이 경로에만 있는 위협</b>이기 때문이다 — 정식 사용자용은 인증된 {@code userId}로 묶이지만 가입용은
 * permitAll이라 이메일·IP 단위 한도가 유일한 남용 방지책이다(메일 폭탄·평판 손상).
 *
 * <p><b>키를 {@code app.auth.web.*} 아래 두지 않는다.</b> 짝을 이루는 것은 기능 토글을 든 {@code
 * app.auth.web.password-reset}이 아니라 <b>{@code app.auth.signup-phone}</b>이다 — 같은 화면의 같은 성격 한도가 두 트리로
 * 갈리면 "가입용 인증 한도가 어디 있더라"를 매번 두 곳에서 찾게 된다. <b>이 클래스에 {@code enabled}가 없는 것도 의도다</b>: 인증 절차 자체에는 환경별
 * 토글이 없고(모든 프로파일에서 같은 흐름), 설정으로 가르는 것은 발송 채널({@code app.mail.enabled})뿐이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth.signup-email")
public class SignupEmailVerificationProperties {

  /** 같은 이메일로 1시간에 허용하는 발송 시도(초과 시 429). */
  private int emailMaxPerHour = 5;

  /** 같은 IP에서 1시간에 허용하는 발송 시도(초과 시 429) — 주소를 바꿔가며 태우는 남용을 막는다. */
  private int ipMaxPerHour = 20;
}
