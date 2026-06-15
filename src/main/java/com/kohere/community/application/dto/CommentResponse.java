package com.kohere.community.application.dto;

import java.time.Instant;

/**
 * 댓글 응답 DTO. 댓글 목록·작성 결과에 쓴다.
 *
 * <p>docs/api/specs/05-community.md 댓글 스키마. 탈퇴 작성자는 닉네임 마스킹한다. {@code editable}은 인증 시 채워진다.
 */
public record CommentResponse(
    Long commentId,
    String content,
    Long authorId,
    String authorNickname,
    String authorNationality,
    Instant createdAt,
    boolean editable) {}
