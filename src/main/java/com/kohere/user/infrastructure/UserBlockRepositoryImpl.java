package com.kohere.user.infrastructure;

import com.kohere.user.domain.UserBlock;
import com.kohere.user.domain.UserBlockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * 사용자 차단 영속 어댑터. 도메인 포트 {@link UserBlockRepository}를 구현하고 Spring Data JPA에 위임한다. 도메인↔엔티티 변환은
 * private static 컨버터로 처리한다(docs/convention/code-style.md §3-3).
 */
@Repository
@RequiredArgsConstructor
public class UserBlockRepositoryImpl implements UserBlockRepository {

  private final UserBlockJpaRepository jpaRepository;

  @Override
  public UserBlock save(UserBlock block) {
    return toDomain(jpaRepository.save(toEntity(block)));
  }

  @Override
  public boolean existsByBlockerIdAndBlockedUserId(long blockerId, long blockedUserId) {
    return jpaRepository.existsByBlockerIdAndBlockedUserId(blockerId, blockedUserId);
  }

  @Override
  public void deleteByBlockerIdAndBlockedUserId(long blockerId, long blockedUserId) {
    jpaRepository.deleteByBlockerIdAndBlockedUserId(blockerId, blockedUserId);
  }

  @Override
  public List<Long> findBlockedUserIdsByBlockerId(long blockerId) {
    return jpaRepository.findBlockedUserIds(blockerId);
  }

  @Override
  public boolean existsBetween(long userA, long userB) {
    return jpaRepository.existsBetween(userA, userB);
  }

  @Override
  public List<UserBlock> findByBlockerId(long blockerId, int page, int size) {
    return jpaRepository
        .findByBlockerId(
            blockerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .stream()
        .map(UserBlockRepositoryImpl::toDomain)
        .toList();
  }

  @Override
  public long countByBlockerId(long blockerId) {
    return jpaRepository.countByBlockerId(blockerId);
  }

  private static UserBlock toDomain(UserBlockJpaEntity e) {
    return UserBlock.builder()
        .id(e.getId())
        .blockerId(e.getBlockerId())
        .blockedUserId(e.getBlockedUserId())
        .createdAt(e.getCreatedAt())
        .build();
  }

  private static UserBlockJpaEntity toEntity(UserBlock b) {
    return UserBlockJpaEntity.builder()
        .id(b.getId())
        .blockerId(b.getBlockerId())
        .blockedUserId(b.getBlockedUserId())
        .createdAt(b.getCreatedAt())
        .build();
  }
}
