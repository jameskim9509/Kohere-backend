package com.kohere.community.presentation.dto;

import com.kohere.community.domain.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 게시글 작성 요청 바디. 입력 형식 검증은 표현 계층에서 수행한다(docs/convention/code-style.md §3-3).
 *
 * <p>docs/api/specs/05-community.md POST /community/posts.
 */
public record CreatePostRequest(
    @NotNull BoardType boardType,
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 5000) String content,
    @Size(max = 10) List<String> hashtags) {}
