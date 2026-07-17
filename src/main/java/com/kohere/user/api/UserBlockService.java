package com.kohere.user.api;

import com.kohere.common.response.PageResponse;
import java.util.List;

/**
 * 사용자 차단 공개 API(Named Interface "api"). user가 {@code user_blocks}를 소유하고, 차단 생성·조회를 공개 명령·쿼리로 노출한다.
 *
 * <p>booking이 예약 상세에서 상대를 도출해 {@link #block}을 호출하고(공개 명령), 예약 목록·상세 필터에 {@link
 * #findBlockedUserIds}를, 신규 예약 신청 양방향 가드에 {@link #isBlockedBetween}을 사용한다(공개 쿼리 · 애플리케이션 레벨 조인,
 * ADR-0002). 차단 목록·해제({@link #listBlocks}·{@link #unblock})는 user 모듈이 자기 엔드포인트로 직접 제공한다.
 */
public interface UserBlockService {

  /** 차단 등록(공개 명령). 이미 차단된 관계면 멱등 처리한다(자기 차단은 호출측이 도출 구조상 발생하지 않는다). */
  void block(long blockerId, long blockedUserId);

  /** 차단 해제. 차단이 아니어도 무연산(멱등). */
  void unblock(long blockerId, long blockedUserId);

  /** 요청자가 차단한 상대 식별자 집합(예약 목록·상세 조회 제외 필터용). */
  List<Long> findBlockedUserIds(long blockerId);

  /** 두 사용자 사이에 어느 방향이든 차단이 있는지(신규 예약 신청 양방향 가드용). */
  boolean isBlockedBetween(long userA, long userB);

  /** 요청자가 차단한 목록을 최신순으로 오프셋 페이지네이션 조회한다(해제 UI용). */
  PageResponse<BlockedUserView> listBlocks(long blockerId, int page, int size);
}
