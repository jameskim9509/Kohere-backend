package com.kohere.listing.api;

import com.kohere.common.response.PageResponse;

/**
 * 매물 추천 공개 쿼리. diagnosis가 진단 조건을 {@link RecommendationCriteria} 값객체로 묶어 동기 호출하면(ADR-0002 Decision
 * 5), listing이 자기 MongoDB 컬렉션만 질의해 매칭 매물 요약 + 좌표를 반환한다(cross-store 조인 없음, ADR-0005 Decision 2).
 *
 * <p>엔티티·내부 enum을 공유하지 않으며 published view({@link RecommendedListingView})로만 결과를 노출한다. 매칭·정렬·페이징 구현은
 * listing 내부(application/infrastructure) 책임이며 본 API 범위 밖이다.
 *
 * <p>스펙: docs/api/specs/02-diagnosis-recommendation.md §7 · 시퀀스 US-2-2.
 */
public interface ListingRecommendationService {

  /**
   * 진단 조건으로 매칭한 추천 매물을 오프셋 페이지로 반환한다(매칭 0건이면 빈 content). 조정 제안(suggestions)·소유권 검증·번역은
   * 호출자(diagnosis) 책임이며 본 쿼리는 매물 매칭 결과만 돌려준다.
   *
   * @param criteria 매칭 조건(지역·예산·조건·대학/지역) + 페이지네이션·정렬
   * @return 추천 매물 요약 + 좌표의 오프셋 페이지
   */
  PageResponse<RecommendedListingView> recommendByCriteria(RecommendationCriteria criteria);
}
