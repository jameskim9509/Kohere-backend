package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.EmailVerification;
import com.kohere.auth.domain.EmailVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 이메일 인증 영속 어댑터(Redis). 도메인 포트 {@link EmailVerificationRepository}를 구현한다(RefreshToken 패턴 미러,
 * ADR-0006).
 *
 * <p>키 {@code email-verify:code:{userId}} = Hash(email·codeHash·attempts·issuedAt·expiresAt),
 * TTL=만료 시각. {@code email-verify:verified:{userId}} = String(검증된 email), TTL=설정값. database-design
 * §4-1 A-2.
 */
@Repository
@RequiredArgsConstructor
public class EmailVerificationRedisRepository implements EmailVerificationRepository {

  private static final String CODE_PREFIX = "email-verify:code:";
  private static final String VERIFIED_PREFIX = "email-verify:verified:";
  private static final String FIELD_EMAIL = "email";
  private static final String FIELD_CODE_HASH = "codeHash";
  private static final String FIELD_ATTEMPTS = "attempts";
  private static final String FIELD_ISSUED_AT = "issuedAt";
  private static final String FIELD_EXPIRES_AT = "expiresAt";

  private final StringRedisTemplate redis;

  @Override
  public void saveChallenge(EmailVerification challenge) {
    String key = CODE_PREFIX + challenge.getUserId();
    redis
        .opsForHash()
        .putAll(
            key,
            Map.of(
                FIELD_EMAIL, challenge.getEmail(),
                FIELD_CODE_HASH, challenge.getCodeHash(),
                FIELD_ATTEMPTS, String.valueOf(challenge.getAttempts()),
                FIELD_ISSUED_AT, String.valueOf(challenge.getIssuedAt().toEpochMilli()),
                FIELD_EXPIRES_AT, String.valueOf(challenge.getExpiresAt().toEpochMilli())));
    redis.expireAt(key, challenge.getExpiresAt());
  }

  @Override
  public Optional<EmailVerification> findChallenge(long userId) {
    Map<Object, Object> entries = redis.opsForHash().entries(CODE_PREFIX + userId);
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        EmailVerification.builder()
            .userId(userId)
            .email((String) entries.get(FIELD_EMAIL))
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
  public void markVerified(long userId, String email, long ttlSeconds) {
    redis.opsForValue().set(VERIFIED_PREFIX + userId, email, Duration.ofSeconds(ttlSeconds));
  }
}
