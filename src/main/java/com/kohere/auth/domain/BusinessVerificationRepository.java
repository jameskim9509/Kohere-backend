package com.kohere.auth.domain;

import java.util.Optional;

/**
 * 사업자등록번호 검증 마커 영속 포트(Redis 백킹). 외부 검증 통과 시 사업자번호 해시를 {@code business-verify:verified:{userId}}에
 * 마킹(TTL=온보딩 토큰 만료 정도)하고, 임대인 온보딩 제출 시 제출 번호 해시와 대조한다. 코드 챌린지가 없는 동기 검증이라 이메일/연락처 인증보다
 * 얇다(ADR-0033). database-design §4-1 A-4.
 */
public interface BusinessVerificationRepository {

  void markVerified(long userId, String businessNumberHash, long ttlSeconds);

  Optional<String> findVerifiedHash(long userId);
}
