package com.kohere.community.application.dto;

/** 좋아요 토글 결과. 변경 후 현재 상태를 담는다. docs/api/specs/05-community.md 좋아요 토글. */
public record LikeToggleResponse(boolean liked, int likeCount) {}
