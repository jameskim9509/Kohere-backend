package com.kohere.listing.presentation.dto;

import com.kohere.listing.domain.Listing;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Collections;
import java.util.Set;

/**
 * 관리자 매물 심사 목록 요청이다({@code GET /api/v1/admin/listings}).
 *
 * <p>{@code status}는 <b>생략하면 전체</b>다. 상태별 조회를 별도 엔드포인트로 나누지 않고 이 파라미터로 흡수한다 — 경로를 나누면 페이지네이션·정렬 계약을
 * 두 벌 문서화·테스트해야 하고 "전체"가 {@code status} 생략과 중복된다.
 *
 * @param status 조회할 상태. 콤마로 여러 개 보낼 수 있다({@code ?status=PENDING,REJECTED})
 * @param sort 정렬 키. 현재는 {@code createdAt,asc}만 인식하고 그 외에는 등록 최신순이다
 */
public record AdminListingSearchRequest(
    Set<Listing.ListingStatus> status, @Min(0) int page, @Min(1) @Max(100) int size, String sort) {

  public AdminListingSearchRequest {
    status = status == null ? Collections.emptySet() : Set.copyOf(status);
    size = size == 0 ? 20 : size;
  }
}
