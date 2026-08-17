package com.kohere.auth.domain;

/**
 * 가입용 SMS 발송 남용 방지 포트(Redis 카운터 백킹, #229 D6). 비로그인 permitAll 경로라 <b>토큰으로 주체를 묶을 수 없어</b> 남는 식별자가 대상
 * 번호와 호출자 IP뿐이고, 둘 다 걸어야 방어가 성립한다 — 번호 한도만 두면 번호를 바꿔가며 발송비를 태울 수 있고, IP 한도만 두면 한 번호를 여러 IP에서 반복 폭격할
 * 수 있다(시퀀스 US-1-13).
 *
 * <p>재발송 쿨다운 60초({@code app.phone.resend-interval-seconds})는 이 포트가 아니라 챌린지의 {@code issuedAt}이 판정한다
 * — 두 방어의 상태가 서로 다른 곳(카운터/챌린지)에 있어서다. 셋 중 무엇을 어겨도 응답은 429 {@code TOO_MANY_REQUESTS} 하나다.
 */
public interface SignupSmsRateLimiter {

  /**
   * 이번 발송 시도를 카운터에 반영하고, 번호·IP 어느 한쪽이라도 시간당 한도를 넘겼으면 거절한다.
   *
   * <p><b>기록이 먼저이고 발송이 나중이다</b> — 발송 성공분만 세면 provider 장애(502)나 발송 실패를 반복하는 것만으로 한도를 무력화할 수 있다. 세는
   * 대상은 "발송된 SMS"가 아니라 "발송 시도"다.
   *
   * @param phoneNumber 정규화된 대상 휴대폰 번호
   * @param clientIp 호출자 IP(프록시 뒤라면 X-Forwarded-For의 최좌측 값). 알 수 없으면 {@code null}·공백이며 이때는 번호 한도만
   *     적용한다
   * @throws PhoneRateLimitException 번호 또는 IP 한도 초과(429)
   */
  void recordAttempt(String phoneNumber, String clientIp);
}
