package com.kohere.auth.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 임대인 웹 회원가입 전 이메일 소유 확인 인증번호 챌린지(단명 — Redis 백킹). 정식(ACTIVE) 사용자용 {@link EmailVerification}과
 * 정책(6자리·코드 TTL 5분·검증 마커 30분·시도 상한 5회)은 같지만 <b>식별자가 다르다</b> — 이쪽은 아직 계정이 없어 {@code userId}가 존재하지
 * 않으므로 <b>정규화한 이메일 자체가 애그리거트 식별자</b>다(database-design §4-1 A-8 · 시퀀스 US-1-18).
 *
 * <p><b>왜 {@link EmailVerification}을 일반화하지 않고 병렬 타입을 두는가</b> — {@link SignupPhoneVerification}이
 * {@link PhoneVerification}에 대해 선 것과 같은 판단이다. 기존 타입은 {@code long userId}를 식별자로 들고 {@link
 * EmailVerificationRepository}의 모든 메서드가 그 타입에 묶여 있어, 식별자를 문자열로 일반화하려면 애그리거트·포트·어댑터·서비스가 함께 흔들린다. 두
 * 채널은 <b>정책만 같고 수명·키 공간·인가 요구가 전혀 다르다</b>(이쪽은 permitAll·이메일 키·IP 레이트리밋 대상). 이미 검증된 경로를 건드리지 않는 쪽이
 * 이득이 커서 병렬 타입을 택했고, 공유해야 할 것(정책값·해시·메일 발송 포트)만 실제로 공유한다.
 *
 * <p>인증번호는 단방향 해시로만 보관한다(원문 미보관). 대상 이메일을 값 필드로 들지 않는 것도 의도다 — 이메일이 곧 키라 값에 또 넣으면 같은 사실이 두 곳에 남아
 * 어긋날 여지만 생긴다(반대로 {@link EmailVerification}은 키가 {@code userId}라 이메일을 값으로 들어야 한다).
 */
@Getter
@Builder(toBuilder = true)
public class SignupEmailVerification {

  /** 정규화(trim + 소문자)된 이메일 — 이 애그리거트의 식별자이자 Redis 키다. */
  private final String email;

  private final String codeHash;
  private final int attempts;
  private final Instant issuedAt;
  private final Instant expiresAt;

  /** 새 인증 시도 발급(attempts=0). {@code email}은 이미 정규화된 값이어야 한다. */
  public static SignupEmailVerification issue(
      String email, String codeHash, Instant now, long ttlSeconds) {
    return SignupEmailVerification.builder()
        .email(email)
        .codeHash(codeHash)
        .attempts(0)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(ttlSeconds))
        .build();
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  /**
   * 입력 인증번호 해시가 일치하는지. 대상 이메일은 대조하지 않는다 — 챌린지를 이메일 키로 찾아온 시점에 이미 같은 주소임이 보장된다({@link
   * EmailVerification#matches}가 이메일까지 보는 것은 키가 {@code userId}라 값의 이메일이 어긋날 수 있어서다).
   */
  public boolean matches(String candidateCodeHash) {
    return codeHash.equals(candidateCodeHash);
  }

  /** 검증 실패 1회 누적(상한 판정용). */
  public SignupEmailVerification incrementAttempt() {
    return toBuilder().attempts(attempts + 1).build();
  }
}
