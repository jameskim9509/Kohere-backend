package com.kohere.auth.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 임대인 온보딩 중 연락처(휴대폰) 소유 확인 인증번호 챌린지(단명 — Redis 백킹). 인증번호는 단방향 해시로만 보관한다(원문 미보관). 검증 실패 누적({@code
 * attempts})이 상한을 넘으면 거부하고, {@code expiresAt} 경과 시 만료다(TTL로 자동 소멸). 세입자 이메일 인증({@link
 * EmailVerification})과 대칭이다(domain-model §1 PhoneVerification · database-design §4-1 A-3 ·
 * ADR-0034).
 */
@Getter
@Builder(toBuilder = true)
public class PhoneVerification {

  private final long userId;
  private final String phoneNumber;
  private final String codeHash;
  private final int attempts;
  private final Instant issuedAt;
  private final Instant expiresAt;

  /** 새 인증 시도 발급(attempts=0). */
  public static PhoneVerification issue(
      long userId, String phoneNumber, String codeHash, Instant now, long ttlSeconds) {
    return PhoneVerification.builder()
        .userId(userId)
        .phoneNumber(phoneNumber)
        .codeHash(codeHash)
        .attempts(0)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(ttlSeconds))
        .build();
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  /** 입력 인증번호 해시와 대상 연락처가 모두 일치하는지. */
  public boolean matches(String candidateCodeHash, String candidatePhoneNumber) {
    return codeHash.equals(candidateCodeHash) && phoneNumber.equals(candidatePhoneNumber);
  }

  /** 검증 실패 1회 누적(상한 판정용). */
  public PhoneVerification incrementAttempt() {
    return toBuilder().attempts(attempts + 1).build();
  }
}
