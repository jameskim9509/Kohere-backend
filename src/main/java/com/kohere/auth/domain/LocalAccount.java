package com.kohere.auth.domain;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * 웹 로컬 자격증명 애그리거트 루트. 임대인 웹의 이메일+비밀번호 로그인 자격을 회원(userId)에 연결한다(ADR-0047). 순수 도메인 모델이며 영속 매핑은
 * infrastructure 어댑터가 처리한다.
 *
 * <p>{@code email} 전역 유니크(웹 로그인 ID) · {@code userId} 유니크(한 계정에 웹 자격증명 하나) — 두 불변식 모두 DB 제약이
 * 정본이다(V22). 소셜 자격 매핑({@link SocialAccount})과 대칭이되, 그쪽이 생성/삭제만 하는 불변 연결인 것과 달리 무차별 대입 방어를 위한 실패
 * 카운터와 잠금 시각을 스스로 들고 상태 전이를 갖는다.
 *
 * <p>{@code name}·{@code birthDate}는 가입 폼 스냅샷이며 회원 프로필(user 소유)의 진본이 아니다 — 기존 계정에 연동할 때 {@code
 * users}를 갱신하지 않으므로 두 값이 다를 수 있다(US-1-11).
 *
 * <p>{@code passwordHash}는 BCrypt 해시로 1급 시크릿이다 — 로그·응답·{@code toString}에 절대 노출하지 않는다(그래서
 * {@code @ToString}을 두지 않는다).
 */
@Getter
@Builder(toBuilder = true)
public class LocalAccount {

  private final Long id;
  private final Long userId;
  private final String email;
  private final String passwordHash;
  private final String name;
  private final LocalDate birthDate;
  private final int failedLoginAttempts;
  private final Instant lockedAt;
  private final Instant createdAt;
  private final Instant updatedAt;

  /** 웹 가입·연동 시 신규 자격증명 등록(실패 0회·잠금 없음). */
  public static LocalAccount register(
      Long userId,
      String email,
      String passwordHash,
      String name,
      LocalDate birthDate,
      Instant now) {
    return LocalAccount.builder()
        .userId(userId)
        .email(email)
        .passwordHash(passwordHash)
        .name(name)
        .birthDate(birthDate)
        .failedLoginAttempts(0)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /** 잠긴 계정인지. 잠금 판정은 자격증명 대조보다 우선한다 — 비밀번호가 맞아도 통과시키지 않는다(US-1-12). */
  public boolean isLocked() {
    return lockedAt != null;
  }

  /**
   * 비밀번호 대조 실패 1회 누적. 누적치가 {@code maxFailedAttempts}에 도달하면 같은 시점으로 잠근다(시간 경과 자동 해제 없음 — 운영자가 {@code
   * locked_at}을 비우는 것이 유일한 해제 경로다).
   *
   * <p>상한은 설정값({@code app.auth.web.login-max-failed-attempts})이라 도메인 상수로 굳히지 않고 인자로 받는다.
   */
  public LocalAccount recordLoginFailure(int maxFailedAttempts, Instant now) {
    int attempts = failedLoginAttempts + 1;
    return toBuilder()
        .failedLoginAttempts(attempts)
        .lockedAt(attempts >= maxFailedAttempts ? now : lockedAt)
        .updatedAt(now)
        .build();
  }

  /** 로그인 성공 — 실패 카운터를 0으로 되돌린다. 잠금이 대조보다 먼저 걸리므로 여기 도달했다면 잠기지 않은 계정이다. */
  public LocalAccount recordLoginSuccess(Instant now) {
    return toBuilder().failedLoginAttempts(0).updatedAt(now).build();
  }
}
