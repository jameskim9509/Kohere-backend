package com.kohere.community.presentation.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 게시글 수정 요청 바디. 전송된 필드만 부분 수정하므로 모든 필드가 선택이다(최소 1개 필요).
 *
 * <p>docs/api/specs/05-community.md PATCH /community/posts/{postId}. TODO: "모두 비어 변경 없음"은 응용 계층에서
 * {@code INVALID_INPUT}으로 검증한다.
 */
public record UpdatePostRequest(
    @Size(max = 100) String title,
    @Size(max = 5000) String content,
    @Size(max = 10) List<String> hashtags) {}
