package com.kohere.auth.domain;

import java.util.Optional;

/**
 * 이메일 인증 상태 영속 포트(Redis 백킹). 인증번호 챌린지({@code email-verify:code:{userId}})와 검증 완료 마커({@code
 * email-verify:verified:{userId}})를 다룬다. 구현은 infrastructure(ADR-0006 패턴). database-design §4-1 A-2.
 */
public interface EmailVerificationRepository {

  /** 인증번호 챌린지 저장(TTL=만료 시각). 재발송 시 기존 시도 대체. */
  void saveChallenge(EmailVerification challenge);

  Optional<EmailVerification> findChallenge(long userId);

  void deleteChallenge(long userId);

  /** 검증 완료 마커 저장(TTL 설정값). verify 성공 표시용(#192에서 온보딩 대조 소비는 폐지, 마커만 유지). */
  void markVerified(long userId, String email, long ttlSeconds);
}
