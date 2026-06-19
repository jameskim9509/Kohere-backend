package com.kohere.user.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 닉네임 사물(뒤 단어) 풀 JPA 엔티티(MySQL {@code nickname_nouns}). 시드 reference 데이터. database-design §4-2. */
@Entity
@Table(name = "nickname_nouns")
@Getter
@NoArgsConstructor
public class NicknameNounJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String word;
  private boolean active;
}
