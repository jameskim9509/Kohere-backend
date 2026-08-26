package com.kohere.auth.domain;

import java.util.Optional;

/**
 * 가입용 이메일 인증 상태 영속 포트(Redis 백킹). 인증번호 챌린지({@code signup-email:code:{정규화이메일}})와 검증 완료 마커({@code
 * signup-email:verified:{정규화이메일}})를 다룬다. 구현은 infrastructure(ADR-0006 패턴). database-design §4-1 A-8.
 *
 * <p>정식 사용자용 {@link EmailVerificationRepository}와 메서드 모양은 같지만 <b>키가 {@code long userId}가 아니라 정규화된
 * 이메일</b>이다 — 가입 전 단계라 계정이 없다. 전달하는 이메일은 <b>모두 정규화된 값</b>이어야 하며, 정규화는 응용 계층 경계에서 한 번만 한다({@code
 * SignupEmailVerificationService}).
 *
 * <p>검증 마커의 <b>소비처는 웹 회원가입 제출(US-1-11) 하나뿐</b>이라 값에 용도 필드를 두지 않는다 — {@link
 * SignupPhoneVerificationRepository}의 마커와 <b>키스페이스를 나눈 것</b>이 곧 용도 구분이다. 필드로 갈랐다면 읽는 쪽이 검사를 잊는 순간
 * 이메일 인증 하나로 연락처 게이트까지 통과하지만, 키가 다르면 애초에 조회가 실패한다.
 */
public interface SignupEmailVerificationRepository {

  /** 인증번호 챌린지 저장(TTL=만료 시각). 재발송·시도 누적 시 기존 레코드를 대체한다. */
  void saveChallenge(SignupEmailVerification challenge);

  Optional<SignupEmailVerification> findChallenge(String email);

  void deleteChallenge(String email);

  /**
   * 검증 완료 마커 저장(TTL=설정값). 값은 존재 자체가 의미인 상수라 이메일을 담지 않는다 — 이메일이 곧 키다({@link
   * EmailVerificationRepository#markVerified}는 키가 {@code userId}라 이메일을 값으로 담는다).
   */
  void markVerified(String email, long ttlSeconds);

  /**
   * 검증 완료 마커가 살아 있는지. 값은 상수이고 <b>키의 존재 자체가 판정</b>이라 값을 읽어 비교하지 않는다(만료는 Redis TTL이 지운다).
   *
   * <p>웹 회원가입 제출의 게이트다 — 없으면 422 {@code AUTH_EMAIL_NOT_VERIFIED}이고 계정 생성도 연동도 하지 않는다.
   */
  boolean isVerified(String email);

  /**
   * 검증 완료 마커 소비(삭제). 가입이 성공한 뒤 한 번 지워 <b>같은 마커로 두 번 가입하는 것</b>을 막는다(1회용).
   *
   * <p>마커가 이미 없어도(TTL 만료·중복 호출) 실패하지 않는 멱등 삭제다.
   */
  void deleteVerified(String email);
}
