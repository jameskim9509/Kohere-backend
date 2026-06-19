package com.kohere.user.domain;

import java.util.List;

/**
 * 닉네임 단어 풀(형용사·사물) 조회 포트. 구현은 infrastructure(reference 테이블 {@code nickname_adjectives}·{@code
 * nickname_nouns}). 활성(active) 단어만 노출한다. database-design §4-2.
 */
public interface NicknameWordRepository {

  /** 활성 형용사(앞 단어) 목록. */
  List<String> findActiveAdjectives();

  /** 활성 사물(뒤 단어) 목록. */
  List<String> findActiveNouns();
}
