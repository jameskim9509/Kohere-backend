package com.kohere.user.domain;

import java.util.List;

/**
 * 사용자 차단 영속 포트. 구현은 infrastructure 계층에 두어 의존성을 역전한다(docs/convention/code-style.md §3-3). 도메인은 영속
 * 기술을 모른다.
 */
public interface UserBlockRepository {

  UserBlock save(UserBlock block);

  boolean existsByBlockerIdAndBlockedUserId(long blockerId, long blockedUserId);

  /** 차단 해제 — 없으면 무연산(멱등). */
  void deleteByBlockerIdAndBlockedUserId(long blockerId, long blockedUserId);

  /** 요청자가 차단한 상대 식별자 집합(예약 목록·상세 조회 필터용). booking이 애플리케이션 레벨 조인으로 사용한다. */
  List<Long> findBlockedUserIdsByBlockerId(long blockerId);

  /** 두 사용자 사이에 어느 방향이든 차단이 있는지(신규 예약 신청 양방향 가드용). */
  boolean existsBetween(long userA, long userB);

  /** 요청자가 차단한 목록을 최신순(createdAt desc)으로 오프셋 조회한다(해제 UI용). */
  List<UserBlock> findByBlockerId(long blockerId, int page, int size);

  long countByBlockerId(long blockerId);
}
