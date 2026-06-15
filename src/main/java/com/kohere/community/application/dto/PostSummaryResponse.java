package com.kohere.community.application.dto;

import com.kohere.community.domain.BoardType;
import java.time.Instant;

/**
 * 게시글 요약 응답 DTO. 목록·내 게시글 목록 항목에 쓴다(표현 계층은 이를 {@code PageResponse}로 감싼다).
 *
 * <p>docs/api/specs/05-community.md 목록 항목 스키마. {@code liked}는 인증 시 채워지며 본인 글 목록에서는 의미가 없을 수 있다.
 */
public record PostSummaryResponse(
    Long postId,
    BoardType boardType,
    String title,
    String authorNickname,
    String authorNationality,
    Instant createdAt,
    int likeCount,
    int commentCount,
    boolean liked) {}
