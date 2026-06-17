package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.RefreshToken;
import com.kohere.auth.domain.RefreshTokenRepository;
import com.kohere.auth.domain.RefreshTokenStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Refresh 토큰 영속 어댑터(Redis). 도메인 포트 {@link RefreshTokenRepository}를 구현한다(ADR-0006).
 *
 * <p>키 {@code refresh:{tokenHash}} = Hash(userId·status·issuedAt·expiresAt), TTL=만료 시각이라 만료 시 폐기
 * 레코드까지 자동 소멸한다. 사용자별 일괄 무효화를 위해 {@code refresh:user:{userId}} = Set(tokenHash) 인덱스를 둔다. 회전·무효화는 키
 * 삭제가 아닌 status 필드 전이로 처리해 폐기 토큰을 만료까지 보존(재사용 탐지)한다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository implements RefreshTokenRepository {

  private static final String KEY_PREFIX = "refresh:";
  private static final String USER_INDEX_PREFIX = "refresh:user:";
  private static final String FIELD_USER_ID = "userId";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_ISSUED_AT = "issuedAt";
  private static final String FIELD_EXPIRES_AT = "expiresAt";

  private final StringRedisTemplate redis;

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    Map<Object, Object> entries = redis.opsForHash().entries(KEY_PREFIX + tokenHash);
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        RefreshToken.builder()
            .tokenHash(tokenHash)
            .userId(Long.valueOf((String) entries.get(FIELD_USER_ID)))
            .status(RefreshTokenStatus.valueOf((String) entries.get(FIELD_STATUS)))
            .issuedAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_ISSUED_AT))))
            .expiresAt(Instant.ofEpochMilli(Long.parseLong((String) entries.get(FIELD_EXPIRES_AT))))
            .build());
  }

  @Override
  public void save(RefreshToken token) {
    String key = KEY_PREFIX + token.getTokenHash();
    redis
        .opsForHash()
        .putAll(
            key,
            Map.of(
                FIELD_USER_ID, String.valueOf(token.getUserId()),
                FIELD_STATUS, token.getStatus().name(),
                FIELD_ISSUED_AT, String.valueOf(token.getIssuedAt().toEpochMilli()),
                FIELD_EXPIRES_AT, String.valueOf(token.getExpiresAt().toEpochMilli())));
    redis.expireAt(key, token.getExpiresAt());

    String userKey = USER_INDEX_PREFIX + token.getUserId();
    redis.opsForSet().add(userKey, token.getTokenHash());
    redis.expireAt(userKey, token.getExpiresAt());
  }

  @Override
  public void revokeAllByUserId(Long userId) {
    Set<String> tokenHashes = redis.opsForSet().members(USER_INDEX_PREFIX + userId);
    if (tokenHashes == null) {
      return;
    }
    for (String tokenHash : tokenHashes) {
      String key = KEY_PREFIX + tokenHash;
      if (Boolean.TRUE.equals(redis.hasKey(key))) {
        // TTL은 유지(만료까지 보존), status만 REVOKED로 전이
        redis.opsForHash().put(key, FIELD_STATUS, RefreshTokenStatus.REVOKED.name());
      }
    }
  }
}
