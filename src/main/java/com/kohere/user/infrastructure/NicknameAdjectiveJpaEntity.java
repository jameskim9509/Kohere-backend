package com.kohere.user.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 닉네임 형용사(앞 단어) 풀 JPA 엔티티(MySQL {@code nickname_adjectives}). 시드 reference 데이터. database-design
 * §4-2.
 */
@Entity
@Table(name = "nickname_adjectives")
@Getter
@NoArgsConstructor
public class NicknameAdjectiveJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String word;
  private boolean active;
}
