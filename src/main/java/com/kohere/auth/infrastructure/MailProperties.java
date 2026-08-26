package com.kohere.auth.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송 채널 스위치({@code app.mail}). {@code app.solapi.enabled}(SMS)의 대칭이며, 값에 따라 {@link
 * com.kohere.auth.domain.VerificationEmailSender} 빈이 갈린다 — {@code true}면 SMTP 실발송({@link
 * SmtpVerificationEmailSender}), 아니면 로깅 폴백({@link LoggingVerificationEmailSender}). 배선은 {@link
 * MailSenderConfig}가 한다.
 *
 * <p><b>이것은 "기능 스위치"가 아니다.</b> 이메일 인증 절차 자체는 모든 실행 환경에서 동일하게 돌며(엔드포인트도 가입 게이트도 조건 없이 등록된다), 이 값은
 * <b>인증번호를 어디로 보내는가</b>만 정한다. 기능 토글로 만들면 꺼진 환경에서 마커를 만들 방법이 없어 가입이 막히거나, 게이트까지 함께 꺼서 <b>인증 없이
 * 가입되는</b> 둘 중 하나가 된다.
 *
 * <p>{@code spring.mail.*}(SMTP 접속 정보)과 트리가 붙어 있는 것은 의도다 — 이 스위치가 켜고 끄는 대상이 바로 그 접속이라 나란히 있는 편이 찾기
 * 쉽다.
 *
 * <p>정책값({@code app.email.*} — 자릿수·TTL·시도 상한)이나 발신 주소({@code app.email.from})와는 다른 트리다. 그쪽은 "인증번호를
 * 어떻게 만들고 누구 이름으로 보내는가"이고 이쪽은 "실제로 보내는가"이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

  /**
   * SMTP 실발송 여부. base 설정에 두지 않고 프로파일에서만 주입한다 — dev·prod는 <b>폴백 없이</b>({@code ${MAIL_ENABLED}}) 받아
   * 주입 누락이 배선 오류로 드러나게 하고, local·test만 기본 {@code false}로 로깅 폴백을 쓴다({@code app.solapi.enabled}와 같은
   * 방식).
   *
   * <p><b>운영에서 이 값이 {@code false}면 조용한 사고다</b> — 사용자는 인증번호를 영원히 받지 못하고 로그에는 평문 인증번호가 쌓인다. 기동은 정상이라
   * 알림도 뜨지 않으므로, 배포 후 dev에서 실제 수신까지 한 번 확인하는 것이 유일한 검증이다.
   */
  private boolean enabled;
}
