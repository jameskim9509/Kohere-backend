package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.SignupEmailVerification;
import com.kohere.auth.domain.SignupEmailVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 가입용 이메일 인증 영속 어댑터(Redis). 도메인 포트 {@link SignupEmailVerificationRepository}를 구현한다(가입용 연락처 인증
 * {@link SignupPhoneVerificationRedisRepository} 미러, ADR-0006).
 *
 * <p>키 {@code signup-email:code:{정규화이메일}} = Hash(codeHash·attempts·issuedAt·expiresAt), TTL=만료 시각.
 * {@code signup-email:verified:{정규화이메일}} = String({@code "1"}), TTL=설정값. database-design §4-1 A-8.
 *
 * <p>정식 사용자용 {@link EmailVerificationRedisRepository}와 값 모양이 하나 다르다 — <b>대상 이메일을 값에 담지 않는다</b>.
 * 이메일이 곧 키라 값에 또 넣으면 같은 사실이 두 곳에 남고, 검증 마커도 존재 자체가 의미라 상수 {@code "1"}만 둔다. 애그리거트의 {@code email}은
 * 조회할 때 키에서 되살린다.
 */
@Repository
@RequiredArgsConstructor
public class SignupEmailVerificationRedisRepository implements SignupEmailVerificationRepository {

  private static final String CODE_PREFIX = "signup-email:code:";
  private static final String VERIFIED_PREFIX = "signup-email:verified:";
  private static final String FIELD_CODE_HASH = "codeHash";
  private static final String FIELD_ATTEMPTS = "attempts";
  private static final String FIELD_ISSUED_AT = "issuedAt";
  private static final String FIELD_EXPIRES_AT = "expiresAt";

  /** 검증 완료 마커 값 — 이메일은 키에 있으므로 값은 존재 표시뿐이다(스펙 §1-12). */
  private static final String VERIFIED_MARKER = "1";

  private final StringRedisTemplate redis;

  @Override
  public void saveChallenge(SignupEmailVerification challenge) {
    String key = CODE_PREFIX + challenge.getEmail();
    redis
        .opsForHash()
        .putAll(
            key,
            Map.of(
                FIELD_CODE_HASH, challenge.getCodeHash(),
                FIELD_ATTEMPTS, String.valueOf(challenge.getAttempts()),
                FIELD_ISSUED_AT, String.valueOf(challenge.getIssuedAt().toEpochMilli()),
                FIELD_EXPIRES_AT, String.valueOf(challenge.getExpiresAt().toEpochMilli())));
    // TTL은 항상 최초 발급 시각 기준 만료로 다시 건다 — attempts 누적 저장이 유효기간을 연장하면 안 된다.
    redis.expireAt(key, challenge.getExpiresAt());
  }

  @Override
  public Optional<SignupEmailVerification> findChallenge(String email) {
    Map<Object, Object> entries = redis.opsForHash().entries(CODE_PREFIX + email);
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        SignupEmailVerification.builder()
            .email(email)
            .codeHash((String) entries.get(FIELD_CODE_HASH))
            .attempts(Integer.parseInt((String) entries.get(FIELD_ATTEMPTS)))
            .issuedAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_ISSUED_AT))))
            .expiresAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_EXPIRES_AT))))
            .build());
  }

  @Override
  public void deleteChallenge(String email) {
    redis.delete(CODE_PREFIX + email);
  }

  @Override
  public void markVerified(String email, long ttlSeconds) {
    redis
        .opsForValue()
        .set(VERIFIED_PREFIX + email, VERIFIED_MARKER, Duration.ofSeconds(ttlSeconds));
  }

  /**
   * 값을 읽지 않고 <b>키 존재만</b> 본다 — 값이 상수 {@code "1"}이라 읽어도 얻을 정보가 없고, 만료는 TTL이 이미 처리한다. {@code hasKey}는
   * {@code Boolean}을 돌려주므로(연결 실패 시 {@code null} 가능) 박싱을 풀지 않고 대조한다.
   */
  @Override
  public boolean isVerified(String email) {
    return Boolean.TRUE.equals(redis.hasKey(VERIFIED_PREFIX + email));
  }

  @Override
  public void deleteVerified(String email) {
    redis.delete(VERIFIED_PREFIX + email);
  }
}
