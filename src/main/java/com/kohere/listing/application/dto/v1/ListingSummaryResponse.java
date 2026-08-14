package com.kohere.listing.application.dto.v1;

import com.kohere.listing.application.dto.CodeLabelResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 매물 목록·키워드 검색의 카드 응답이다.
 *
 * <p>상세 응답과 같은 다국어 규칙을 사용한다. {@code code}는 요청/비교용, {@code label}은 현재 언어의 화면 표시용이다.
 */
public record ListingSummaryResponse(
    String listingId,
    String title,
    CodeLabelResponse type,
    ListingDetailResponse.ListingStatus status,
    CodeLabelResponse rentalType,
    ListingDetailResponse.RefundPolicyResponse refundPolicy,
    ListingDetailResponse.Contract contract,
    CodeLabelResponse genderPolicy,
    ListingDetailResponse.GeoPoint location,
    ListingDetailResponse.AddressResponse address,
    ListingDetailResponse.NearestTransitResponse nearestTransit,
    Set<String> nearbyUniversityCodes,
    ListingDetailResponse.BuildingResponse building,
    ListingDetailResponse.PropertyPolicies propertyPolicies,
    ListingDetailResponse.FacilitiesResponse facilities,
    List<CodeLabelResponse> conditions,
    List<ListingDetailResponse.RoomOfferResponse> roomOffers,
    ListingDetailResponse.DescriptionsResponse descriptions,
    List<String> imageUrls,
    Integer distanceMeters,
    boolean favorited,
    int favoriteCount,
    Instant createdAt,
    Instant updatedAt) {}
