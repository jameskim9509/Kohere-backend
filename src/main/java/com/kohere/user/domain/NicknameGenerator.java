package com.kohere.user.domain;

/**
 * 닉네임 생성 도메인 서비스. 형용사 풀·사물 풀(reference 데이터)에서 무작위로 각 1개를 골라 {@code 형용사 + 사물}로 조합하고, {@code
 * users.nickname} 전역 유니크 충돌 시 재조합 재시도(상한 도달 시 fallback)해 유일한 닉네임을 반환한다. domain-model §2(닉네임 생성).
 */
public interface NicknameGenerator {

  /** 형용사+사물 조합으로 전역 유니크가 보장되는 닉네임을 생성해 반환한다. */
  String generateUnique();
}
