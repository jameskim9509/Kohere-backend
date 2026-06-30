package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.BusinessVerificationRepository;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사업자등록번호 검증 마커 영속 어댑터(Redis). 도메인 포트 {@link BusinessVerificationRepository}를 구현한다(ADR-0006 패턴).
 *
 * <p>키 {@code business-verify:verified:{userId}} = String(검증된 사업자번호 SHA-256 해시), TTL=설정값. 코드 챌린지가
 * 없는 동기 검증이라 검증 완료 마커만 다룬다. database-design §4-1 A-4.
 */
@Repository
@RequiredArgsConstructor
public class BusinessVerificationRedisRepository implements BusinessVerificationRepository {

  private static final String VERIFIED_PREFIX = "business-verify:verified:";

  private final StringRedisTemplate redis;

  @Override
  public void markVerified(long userId, String businessNumberHash, long ttlSeconds) {
    redis
        .opsForValue()
        .set(VERIFIED_PREFIX + userId, businessNumberHash, Duration.ofSeconds(ttlSeconds));
  }

  @Override
  public Optional<String> findVerifiedHash(long userId) {
    return Optional.ofNullable(redis.opsForValue().get(VERIFIED_PREFIX + userId));
  }
}
