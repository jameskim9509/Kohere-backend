package com.kohere.auth.domain;

/**
 * 연락처 인증번호 단방향 해시 포트(SHA-256 + pepper). 인증번호 원문은 저장·로그하지 않고 해시만 보관하며, 검증은 입력 해시와 저장 해시를 대조한다(이메일
 * 인증번호 해시와 대칭).
 */
public interface PhoneVerificationCodeHasher {

  String hash(String code);
}
