package com.kohere.listing.application.dto;

/** 찜 토글 결과. 변경 후 현재 상태를 담는다. docs/api/specs/03-listings-favorites.md 토글 액션. */
public record FavoriteToggleResponse(boolean favorited, int favoriteCount) {}
