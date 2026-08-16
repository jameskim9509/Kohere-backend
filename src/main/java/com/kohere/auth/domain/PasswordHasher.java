package com.kohere.auth.domain;

/**
 * 웹 로그인 비밀번호의 단방향 해시 포트(BCrypt 구현은 infrastructure). 원문은 저장·로그하지 않고 해시만 보관한다.
 *
 * <p>인증번호 해시({@link PhoneVerificationCodeHasher})와 달리 등치 조회가 필요 없어 salt를 품는 adaptive hash를 쓴다 — 같은
 * 원문이라도 매번 다른 해시가 나오므로 대조는 문자열 비교가 아니라 반드시 {@link #matches}로 한다.
 */
public interface PasswordHasher {

  String hash(String rawPassword);

  /** 원문이 저장된 해시와 같은 비밀번호인지. */
  boolean matches(String rawPassword, String passwordHash);
}
