package com.kohere.user.api;

/** 계정 식별·상태 조회 결과(모듈 간 전달용). status는 원시 문자열(PENDING/ACTIVE/WITHDRAWN)로 노출해 내부 enum을 공유하지 않는다. */
public record UserAccountView(long userId, String status) {}
