package com.kohere.listing.application.dto.v1;

import com.kohere.common.response.PageInfo;
import com.kohere.listing.application.dto.MatchedPlaceResponse;
import java.util.List;

/**
 * 개정 전 키워드 검색 응답이다. 껍데기는 v2와 같지만 {@code content} 원소가 개정 전 {@link ListingSummaryResponse}라 v1 쪽에 따로
 * 둔다.
 *
 * <p>{@code MatchedPlaceResponse}는 개정 전후가 같아 그대로 쓴다.
 */
public record ListingKeywordSearchResponse(
    MatchedPlaceResponse matchedPlace, List<ListingSummaryResponse> content, PageInfo page) {}
