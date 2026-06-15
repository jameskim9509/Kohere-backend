package com.kohere.community.application.dto;

import com.kohere.community.domain.BoardType;
import java.time.Instant;
import java.util.List;

/**
 * 게시글 상세 응답 DTO. 작성/수정 결과와 상세 조회에 쓴다.
 *
 * <p>docs/api/specs/05-community.md 상세 스키마. 탈퇴 작성자는 {@code authorNickname} 마스킹·{@code
 * authorNationality} null 처리한다. {@code liked}·{@code editable}은 인증 시 채워진다.
 */
public record PostDetailResponse(
    Long postId,
    BoardType boardType,
    String title,
    String content,
    List<String> hashtags,
    Long authorId,
    String authorNickname,
    String authorNationality,
    Instant createdAt,
    Instant updatedAt,
    int likeCount,
    int commentCount,
    int shareCount,
    boolean liked,
    boolean editable) {}
