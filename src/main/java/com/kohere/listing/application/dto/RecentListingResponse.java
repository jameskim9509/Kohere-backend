package com.kohere.listing.application.dto;

import com.kohere.listing.domain.Listing;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 최근 본 매물 카드 응답이다. 상세 응답과 같은 번역 구조에 마지막 조회 시각을 더한다. */
public record RecentListingResponse(
    String listingId,
    String title,
    CodeLabelResponse type,
    Listing.ListingStatus status,
    CodeLabelResponse rentalType,
    String refundPolicy,
    CodeLabelResponse genderPolicy,
    CodeLabelResponse arcRequired,
    int ageMin,
    int ageMax,
    List<CodeLabelResponse> languagesSupported,
    ListingDetailResponse.ContactResponse contact,
    String blogUrl,
    ListingDetailResponse.GeoPoint location,
    ListingDetailResponse.AddressResponse address,
    ListingDetailResponse.NearestTransitResponse nearestTransit,
    List<CodeLabelResponse> nearbyFacilities,
    Set<String> nearbyUniversityCodes,
    ListingDetailResponse.BuildingResponse building,
    ListingDetailResponse.FacilitiesResponse facilities,
    List<CodeLabelResponse> conditions,
    List<ListingDetailResponse.RoomOfferResponse> roomOffers,
    String description,
    String extraNotes,
    List<String> imageUrls,
    boolean favorited,
    int favoriteCount,
    Instant createdAt,
    Instant updatedAt,
    Instant viewedAt) {}
