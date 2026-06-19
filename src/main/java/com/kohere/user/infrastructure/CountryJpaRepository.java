package com.kohere.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 리포지토리(내부). {@code CountryRepository} 어댑터가 사용한다. PK는 ISO 코드(String). */
interface CountryJpaRepository extends JpaRepository<CountryJpaEntity, String> {}
