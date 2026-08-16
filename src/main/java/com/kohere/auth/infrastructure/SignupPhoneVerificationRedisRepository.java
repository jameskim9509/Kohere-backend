package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.SignupPhoneVerification;
import com.kohere.auth.domain.SignupPhoneVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 가입용 연락처 인증 영속 어댑터(Redis). 도메인 포트 {@link SignupPhoneVerificationRepository}를 구현한다(온보딩용 {@link
 * PhoneVerificationRedisRepository} 미러, ADR-0006).
 *
 * <p>키 {@code signup-phone:code:{정규화번호}} = Hash(codeHash·attempts·issuedAt·expiresAt), TTL=만료 시각.
 * {@code signup-phone:verified:{정규화번호}} = String({@code "1"}), TTL=설정값. database-design §4-1 A-5.
 *
 * <p>온보딩용 어댑터와 값 모양이 하나 다르다 — <b>대상 번호를 값에 담지 않는다</b>. 번호가 곧 키라 값에 또 넣으면 같은 사실이 두 곳에 남고, 검증 마커도 존재
 * 자체가 의미라 상수 {@code "1"}만 둔다. 애그리거트의 {@code phoneNumber}는 조회할 때 키에서 되살린다.
 */
@Repository
@RequiredArgsConstructor
public class SignupPhoneVerificationRedisRepository implements SignupPhoneVerificationRepository {

  private static final String CODE_PREFIX = "signup-phone:code:";
  private static final String VERIFIED_PREFIX = "signup-phone:verified:";
  private static final String FIELD_CODE_HASH = "codeHash";
  private static final String FIELD_ATTEMPTS = "attempts";
  private static final String FIELD_ISSUED_AT = "issuedAt";
  private static final String FIELD_EXPIRES_AT = "expiresAt";

  /** 검증 완료 마커 값 — 번호는 키에 있으므로 값은 존재 표시뿐이다(스펙 §1-2). */
  private static final String VERIFIED_MARKER = "1";

  private final StringRedisTemplate redis;

  @Override
  public void saveChallenge(SignupPhoneVerification challenge) {
    String key = CODE_PREFIX + challenge.getPhoneNumber();
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
  public Optional<SignupPhoneVerification> findChallenge(String phoneNumber) {
    Map<Object, Object> entries = redis.opsForHash().entries(CODE_PREFIX + phoneNumber);
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        SignupPhoneVerification.builder()
            .phoneNumber(phoneNumber)
            .codeHash((String) entries.get(FIELD_CODE_HASH))
            .attempts(Integer.parseInt((String) entries.get(FIELD_ATTEMPTS)))
            .issuedAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_ISSUED_AT))))
            .expiresAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_EXPIRES_AT))))
            .build());
  }

  @Override
  public void deleteChallenge(String phoneNumber) {
    redis.delete(CODE_PREFIX + phoneNumber);
  }

  @Override
  public void markVerified(String phoneNumber, long ttlSeconds) {
    redis
        .opsForValue()
        .set(VERIFIED_PREFIX + phoneNumber, VERIFIED_MARKER, Duration.ofSeconds(ttlSeconds));
  }

  /**
   * 값을 읽지 않고 <b>키 존재만</b> 본다 — 값이 상수 {@code "1"}이라 읽어도 얻을 정보가 없고, 만료는 TTL이 이미 처리한다. {@code hasKey}는
   * {@code Boolean}을 돌려주므로(연결 실패 시 {@code null} 가능) 박싱을 풀지 않고 대조한다.
   */
  @Override
  public boolean isVerified(String phoneNumber) {
    return Boolean.TRUE.equals(redis.hasKey(VERIFIED_PREFIX + phoneNumber));
  }

  @Override
  public void deleteVerified(String phoneNumber) {
    redis.delete(VERIFIED_PREFIX + phoneNumber);
  }
}
