package com.kohere.gamification.domain;

/**
 * 4지선다 보기 한 개. {@code key}는 단일 대문자 보기 키({@code A}~{@code D})이며 의미를 갖는 도메인 enum이 아니다.
 * docs/api/specs/06-gamification.md (choices).
 */
public record QuizChoice(String key, String text) {}
