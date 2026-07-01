package com.kohere.auth.domain;

import java.util.Optional;

/**
 * 연락처 인증 상태 영속 포트(Redis 백킹). 인증번호 챌린지({@code phone-verify:code:{userId}})와 검증 완료 마커({@code
 * phone-verify:verified:{userId}})를 다룬다. 구현은 infrastructure(ADR-0006 패턴). database-design §4-1 A-3.
 */
public interface PhoneVerificationRepository {

  /** 인증번호 챌린지 저장(TTL=만료 시각). 재발송 시 기존 시도 대체. */
  void saveChallenge(PhoneVerification challenge);

  Optional<PhoneVerification> findChallenge(long userId);

  void deleteChallenge(long userId);

  /** 검증 완료 마커 저장(TTL=온보딩 토큰 만료 정도). 온보딩 제출·프로필 변경 시 제출 phoneNumber와 대조한다. */
  void markVerified(long userId, String phoneNumber, long ttlSeconds);

  Optional<String> findVerifiedPhone(long userId);
}
