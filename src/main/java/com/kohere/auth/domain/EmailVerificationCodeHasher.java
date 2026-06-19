package com.kohere.auth.domain;

/**
 * 이메일 인증번호의 단방향 해시 포트. 저장소에는 원문이 아닌 {@code SHA-256(code + pepper)}만 보관한다. 등치 비교가 필요하므로 adaptive
 * hash는 쓰지 않는다(refresh 토큰 해시와 동형, ADR-0006).
 */
public interface EmailVerificationCodeHasher {

  String hash(String code);
}
