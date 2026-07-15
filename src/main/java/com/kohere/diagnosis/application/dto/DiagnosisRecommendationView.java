package com.kohere.diagnosis.application.dto;

import com.kohere.common.response.PageInfo;
import com.kohere.common.response.PageResponse;
import com.kohere.listing.api.RecommendedListingView;
import java.util.List;

/**
 * v2 자동 확정({@code COMPLETED}) 시 함께 내려주는 추천 결과(추천 매물 + 지도 좌표 + 페이지 메타). v1 {@link
 * RecommendationResponse}의 중첩 {@code RecommendedListing}/{@code MapMarker}를 재사용하되, v2는 조정 제안({@code
 * suggestions})을 두지 않는다(issue #157·ADR-0036).
 *
 * <p>첫 페이지만 인라인으로 담으며, 이후 페이지네이션은 {@code diagnosisId}로 v1 {@code GET
 * /api/v1/diagnoses/{id}/recommendations}를 호출해 조회한다.
 *
 * @param content 추천 매물 요약 목록(현재 페이지)
 * @param markers 현재 페이지 매물의 지도 좌표
 * @param page 오프셋 페이지 메타
 */
public record DiagnosisRecommendationView(
    List<RecommendationResponse.RecommendedListing> content,
    List<RecommendationResponse.MapMarker> markers,
    PageInfo page) {

  /** listing 공개 추천 결과를 v2 추천 뷰로 매핑한다(v1과 동일 필드 복사). */
  public static DiagnosisRecommendationView from(PageResponse<RecommendedListingView> result) {
    List<RecommendationResponse.RecommendedListing> content =
        result.content().stream()
            .map(
                v ->
                    new RecommendationResponse.RecommendedListing(
                        v.listingId(),
                        v.title(),
                        v.type(),
                        v.monthlyRentMin(),
                        v.monthlyRentMax(),
                        v.minDeposit(),
                        v.maxDeposit(),
                        v.thumbnailUrl(),
                        v.lat(),
                        v.lng(),
                        v.conditions()))
            .toList();
    List<RecommendationResponse.MapMarker> markers =
        result.content().stream()
            .map(v -> new RecommendationResponse.MapMarker(v.listingId(), v.lat(), v.lng()))
            .toList();
    return new DiagnosisRecommendationView(content, markers, result.page());
  }
}
