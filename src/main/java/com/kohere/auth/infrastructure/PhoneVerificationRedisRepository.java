package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.PhoneVerification;
import com.kohere.auth.domain.PhoneVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 연락처 인증 영속 어댑터(Redis). 도메인 포트 {@link PhoneVerificationRepository}를 구현한다(이메일 인증 어댑터 미러, ADR-0006).
 *
 * <p>키 {@code phone-verify:code:{userId}} = Hash(phoneNumber·codeHash·attempts·issuedAt·expiresAt),
 * TTL=만료 시각. {@code phone-verify:verified:{userId}} = String(검증된 phoneNumber), TTL=설정값.
 * database-design §4-1 A-3.
 */
@Repository
@RequiredArgsConstructor
public class PhoneVerificationRedisRepository implements PhoneVerificationRepository {

  private static final String CODE_PREFIX = "phone-verify:code:";
  private static final String VERIFIED_PREFIX = "phone-verify:verified:";
  private static final String FIELD_PHONE = "phoneNumber";
  private static final String FIELD_CODE_HASH = "codeHash";
  private static final String FIELD_ATTEMPTS = "attempts";
  private static final String FIELD_ISSUED_AT = "issuedAt";
  private static final String FIELD_EXPIRES_AT = "expiresAt";

  private final StringRedisTemplate redis;

  @Override
  public void saveChallenge(PhoneVerification challenge) {
    String key = CODE_PREFIX + challenge.getUserId();
    redis
        .opsForHash()
        .putAll(
            key,
            Map.of(
                FIELD_PHONE, challenge.getPhoneNumber(),
                FIELD_CODE_HASH, challenge.getCodeHash(),
                FIELD_ATTEMPTS, String.valueOf(challenge.getAttempts()),
                FIELD_ISSUED_AT, String.valueOf(challenge.getIssuedAt().toEpochMilli()),
                FIELD_EXPIRES_AT, String.valueOf(challenge.getExpiresAt().toEpochMilli())));
    redis.expireAt(key, challenge.getExpiresAt());
  }

  @Override
  public Optional<PhoneVerification> findChallenge(long userId) {
    Map<Object, Object> entries = redis.opsForHash().entries(CODE_PREFIX + userId);
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        PhoneVerification.builder()
            .userId(userId)
            .phoneNumber((String) entries.get(FIELD_PHONE))
            .codeHash((String) entries.get(FIELD_CODE_HASH))
            .attempts(Integer.parseInt((String) entries.get(FIELD_ATTEMPTS)))
            .issuedAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_ISSUED_AT))))
            .expiresAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_EXPIRES_AT))))
            .build());
  }

  @Override
  public void deleteChallenge(long userId) {
    redis.delete(CODE_PREFIX + userId);
  }

  @Override
  public void markVerified(long userId, String phoneNumber, long ttlSeconds) {
    redis.opsForValue().set(VERIFIED_PREFIX + userId, phoneNumber, Duration.ofSeconds(ttlSeconds));
  }

  @Override
  public Optional<String> findVerifiedPhone(long userId) {
    return Optional.ofNullable(redis.opsForValue().get(VERIFIED_PREFIX + userId));
  }
}
