package com.kohere.auth.domain;

import java.util.Optional;

/**
 * 웹 로컬 자격증명 영속 포트. 구현은 infrastructure에 둔다(의존성 역전, {@link SocialAccountRepository}와 대칭).
 *
 * <p>유일성의 정본은 DB 유니크 제약(V22)이고 {@code existsBy*}는 그 위반을 사용자에게 알맞은 에러 코드로 바꿔 주기 위한 선검사다 — 동시 요청은
 * 선검사를 통과해도 INSERT에서 막힌다.
 */
public interface LocalAccountRepository {

  /** 웹 로그인 — 이메일이 로그인 ID다. 미존재와 비밀번호 불일치는 호출부가 같은 401로 묶는다(계정 존재 여부 비노출). */
  Optional<LocalAccount> findByEmail(String email);

  Optional<LocalAccount> findByUserId(Long userId);

  /** 가입 시 로그인 ID 중복 선검사 — 위반은 409 {@code AUTH_EMAIL_ALREADY_REGISTERED}. */
  boolean existsByEmail(String email);

  /** 번호로 찾은 기존 계정에 이미 웹 자격증명이 붙었는지 선검사 — 위반은 409 {@code AUTH_WEB_ACCOUNT_ALREADY_EXISTS}. */
  boolean existsByUserId(Long userId);

  LocalAccount save(LocalAccount localAccount);
}
