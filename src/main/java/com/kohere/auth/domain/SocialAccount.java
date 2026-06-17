package com.kohere.auth.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 소셜 자격 매핑 애그리거트 루트. 검증된 소셜 신원(provider+providerUserId)을 회원(userId)에 연결한다. 순수 도메인 모델이며 영속 매핑은
 * infrastructure 어댑터가 처리한다.
 *
 * <p>(provider, providerUserId) 전역 유니크 — 로그인 분기의 등치 조회 키. 회원 상태/프로필은 user 소유이므로 userId 값만
 * 참조한다(ADR-0002). 매핑은 생성/삭제만 하는 불변 연결이다(database-design §4-1).
 */
@Getter
@Builder
public class SocialAccount {

  private final Long id;
  private final Provider provider;
  private final String providerUserId;
  private final String email;
  private final Long userId;
  private final Instant linkedAt;
}
