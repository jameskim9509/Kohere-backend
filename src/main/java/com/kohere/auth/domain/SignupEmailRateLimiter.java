package com.kohere.auth.domain;

/**
 * 가입용 이메일 발송 남용 방지 포트(Redis 카운터 백킹). 비로그인 permitAll 경로라 <b>토큰으로 주체를 묶을 수 없어</b> 남는 식별자가 대상 이메일과 호출자
 * IP뿐이고, 둘 다 걸어야 방어가 성립한다 — 이메일 한도만 두면 주소를 바꿔가며 태울 수 있고, IP 한도만 두면 한 주소를 여러 IP에서 반복 폭격할 수 있다(시퀀스
 * US-1-18). {@link SignupSmsRateLimiter}의 이메일 채널 대응물이다.
 *
 * <p>재발송 쿨다운 60초({@code app.email.resend-interval-seconds})는 이 포트가 아니라 챌린지의 {@code issuedAt}이 판정한다
 * — 두 방어의 상태가 서로 다른 곳(카운터/챌린지)에 있어서다. 셋 중 무엇을 어겨도 응답은 429 {@code TOO_MANY_REQUESTS} 하나다.
 *
 * <p><b>예산은 다른 채널과 나눠 갖는다</b>({@code app.auth.signup-email.*}) — 가입용 SMS·이메일 찾기·재설정 링크와 버킷을 공유하면
 * 사용자가 화면을 오가는 것만으로 서로의 몫을 태워 두세 번 만에 429가 난다.
 */
public interface SignupEmailRateLimiter {

  /**
   * 이번 발송 시도를 카운터에 반영하고, 이메일·IP 어느 한쪽이라도 시간당 한도를 넘겼으면 거절한다.
   *
   * <p><b>기록이 먼저이고 발송이 나중이다</b> — 발송 성공분만 세면 provider 장애(502)나 발송 실패를 반복하는 것만으로 한도를 무력화할 수 있다. 세는
   * 대상은 "발송된 메일"이 아니라 "발송 시도"다.
   *
   * <p><b>로그인 ID 중복 판정보다도 먼저다</b> — 중복 검사를 앞에 두면 카운터를 하나도 올리지 않고 주소를 무한히 물어볼 수 있어 가입 여부 열거가 공짜가 되고,
   * 그 판정은 익명 호출자가 유발하는 DB 읽기이기도 하다. 다만 이 한도는 <b>비용 상한이지 열거 방어가 아니다</b>(이메일 축은 한 주소당 한 번만 묻는 관찰에
   * 무력하고, IP 축은 {@code X-Forwarded-For}라 위조 가능하다).
   *
   * @param email 정규화된 대상 이메일
   * @param clientIp 호출자 IP(프록시 뒤라면 X-Forwarded-For의 최좌측 값). 알 수 없으면 {@code null}·공백이며 이때는 이메일 한도만
   *     적용한다
   * @throws EmailRateLimitException 이메일 또는 IP 한도 초과(429)
   */
  void recordAttempt(String email, String clientIp);
}
