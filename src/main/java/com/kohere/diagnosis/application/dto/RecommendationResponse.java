package com.kohere.diagnosis.application.dto;

import java.util.List;

/**
 * 진단 결과(추천 매물) 단일 항목 응답 DTO. 추천 매물 요약과 지도 마커를 담는다.
 *
 * <p>스펙상 매물 요약은 매물 탐색(listing) 도메인의 {@code ListingSummary}를 재사용하지만, 스켈레톤에서는 모듈 경계를 지키기 위해 자체 {@link
 * RecommendedListing} record로 둔다. TODO: 매물 탐색 모듈 공개 API·이벤트로 매물 데이터를 연결한다(listing 직접 import 금지).
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md §5.
 *
 * @param content 추천 매물 요약 목록(현재 페이지)
 * @param markers 현재 페이지 매물의 지도 좌표
 * @param suggestions 매칭 0건일 때 조건/예산 조정 제안(결과가 있으면 null)
 */
public record RecommendationResponse(
    List<RecommendedListing> content, List<MapMarker> markers, Suggestions suggestions) {

  /** 추천 매물 요약(자체 정의). TODO: 매물 탐색 도메인 ListingSummary로 동기화한다. */
  public record RecommendedListing(
      Long listingId, String title, int monthlyRent, int deposit, double lat, double lng) {}

  /** 지도 마커 좌표. */
  public record MapMarker(Long listingId, double lat, double lng) {}

  /** 매칭 0건일 때의 조정 제안. {@code reason}/{@code actions[].type}는 클라이언트 다국어 매핑 기준. */
  public record Suggestions(String reason, String message, List<SuggestionAction> actions) {}

  /** 조정 제안 액션. TODO: type enum 카탈로그 확정(RELAX_REGION/RELAX_CONDITIONS/INCREASE_BUDGET 등). */
  public record SuggestionAction(String type, String detail) {}
}
