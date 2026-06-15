package com.kohere.community.application.dto;

/** 공유 카운트 증가 결과. 증가 후 현재 {@code shareCount}를 담는다. docs/api/specs/05-community.md 공유 카운트. */
public record ShareResponse(int shareCount) {}
