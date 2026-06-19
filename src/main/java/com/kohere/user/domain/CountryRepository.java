package com.kohere.user.domain;

import java.util.Optional;

/**
 * 국가 참조 포트. 구현은 infrastructure(reference 테이블 {@code countries}). 온보딩/수정 시 국가 코드 유효성 검증과 표시명·국기
 * resolve에 쓴다. database-design §4-2.
 */
public interface CountryRepository {

  /** 주어진 ISO 코드의 국가가 존재하는지. 온보딩/수정 입력 검증용. */
  boolean existsByCode(String code);

  /** 국가 코드로 표시명·국기를 resolve(없으면 비어 있음). */
  Optional<Country> findByCode(String code);
}
