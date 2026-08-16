package com.kohere.auth.infrastructure;

import com.kohere.auth.domain.LocalAccount;
import com.kohere.auth.domain.LocalAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 웹 로컬 자격증명 영속 어댑터. 도메인 포트 {@link LocalAccountRepository}를 JPA로 구현하고 도메인↔엔티티를 매핑한다(의존성 역전). */
@Repository
@RequiredArgsConstructor
public class LocalAccountRepositoryImpl implements LocalAccountRepository {

  private final LocalAccountJpaRepository jpaRepository;

  @Override
  public Optional<LocalAccount> findByEmail(String email) {
    return jpaRepository.findByEmail(email).map(LocalAccountRepositoryImpl::toDomain);
  }

  @Override
  public Optional<LocalAccount> findByUserId(Long userId) {
    return jpaRepository.findByUserId(userId).map(LocalAccountRepositoryImpl::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return jpaRepository.existsByEmail(email);
  }

  @Override
  public boolean existsByUserId(Long userId) {
    return jpaRepository.existsByUserId(userId);
  }

  @Override
  public LocalAccount save(LocalAccount localAccount) {
    return toDomain(jpaRepository.save(toEntity(localAccount)));
  }

  private static LocalAccount toDomain(LocalAccountJpaEntity e) {
    return LocalAccount.builder()
        .id(e.getId())
        .userId(e.getUserId())
        .email(e.getEmail())
        .passwordHash(e.getPasswordHash())
        .name(e.getName())
        .birthDate(e.getBirthDate())
        .failedLoginAttempts(e.getFailedLoginAttempts())
        .lockedAt(e.getLockedAt())
        .createdAt(e.getCreatedAt())
        .updatedAt(e.getUpdatedAt())
        .build();
  }

  private static LocalAccountJpaEntity toEntity(LocalAccount a) {
    return LocalAccountJpaEntity.builder()
        .id(a.getId())
        .userId(a.getUserId())
        .email(a.getEmail())
        .passwordHash(a.getPasswordHash())
        .name(a.getName())
        .birthDate(a.getBirthDate())
        .failedLoginAttempts(a.getFailedLoginAttempts())
        .lockedAt(a.getLockedAt())
        .createdAt(a.getCreatedAt())
        .updatedAt(a.getUpdatedAt())
        .build();
  }
}
