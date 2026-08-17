package com.kohere.auth.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 임대인 웹 회원가입 전 연락처(휴대폰) 소유 확인 인증번호 챌린지(단명 — Redis 백킹). 온보딩용 {@link PhoneVerification}과 정책(6자리·코드
 * TTL 5분·검증 마커 30분·시도 상한 5회)은 같지만 <b>식별자가 다르다</b> — 이쪽은 아직 계정이 없어 {@code userId}가 존재하지 않으므로 <b>정규화한
 * 휴대폰 번호 자체가 애그리거트 식별자</b>다(database-design §4-1 A-5 · 시퀀스 US-1-13).
 *
 * <p><b>왜 {@link PhoneVerification}을 일반화하지 않고 병렬 타입을 두는가</b> — 기존 타입은 {@code long userId}를 식별자로 들고
 * {@link PhoneVerificationRepository}의 모든 메서드가 그 타입에 묶여 있다. 식별자를 문자열로 일반화하려면 애그리거트·포트·어댑터·서비스와 그 위의
 * 온보딩(US-1-9)·프로필 연락처 변경(US-1-5) 경로까지 함께 흔들리는데, 두 채널은 <b>정책만 같고 수명·키 공간·인가 요구가 전혀 다르다</b>(이쪽은
 * permitAll·번호 키·IP 레이트리밋 대상). 이미 검증된 앱 경로를 건드리지 않는 쪽이 이득이 커서 병렬 타입을 택했고, 공유해야 할 것(정책값·해시·SMS 발송
 * 포트)만 실제로 공유한다.
 *
 * <p>인증번호는 단방향 해시로만 보관한다(원문 미보관). 대상 번호를 값 필드로 들지 않는 것도 의도다 — 번호가 곧 키라 값에 또 넣으면 같은 사실이 두 곳에 남아 어긋날
 * 여지만 생긴다(반대로 {@link PhoneVerification}은 키가 {@code userId}라 번호를 값으로 들어야 한다).
 */
@Getter
@Builder(toBuilder = true)
public class SignupPhoneVerification {

  /** 정규화(숫자만)된 휴대폰 번호 — 이 애그리거트의 식별자이자 Redis 키다. */
  private final String phoneNumber;

  private final String codeHash;
  private final int attempts;
  private final Instant issuedAt;
  private final Instant expiresAt;

  /** 새 인증 시도 발급(attempts=0). {@code phoneNumber}는 이미 정규화된 값이어야 한다. */
  public static SignupPhoneVerification issue(
      String phoneNumber, String codeHash, Instant now, long ttlSeconds) {
    return SignupPhoneVerification.builder()
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

  /**
   * 입력 인증번호 해시가 일치하는지. 대상 번호는 대조하지 않는다 — 챌린지를 번호 키로 찾아온 시점에 이미 같은 번호임이 보장된다({@link
   * PhoneVerification#matches}가 번호까지 보는 것은 키가 {@code userId}라 값의 번호가 어긋날 수 있어서다).
   */
  public boolean matches(String candidateCodeHash) {
    return codeHash.equals(candidateCodeHash);
  }

  /** 검증 실패 1회 누적(상한 판정용). */
  public SignupPhoneVerification incrementAttempt() {
    return toBuilder().attempts(attempts + 1).build();
  }
}
