package com.kohere.community.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 댓글 작성 요청 바디. docs/api/specs/05-community.md POST /community/posts/{postId}/comments. */
public record CreateCommentRequest(@NotBlank @Size(max = 1000) String content) {}
