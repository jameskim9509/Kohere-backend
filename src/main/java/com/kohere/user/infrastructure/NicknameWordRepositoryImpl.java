package com.kohere.user.infrastructure;

import com.kohere.user.domain.NicknameWordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 닉네임 단어 풀 어댑터. 도메인 포트 {@link NicknameWordRepository}를 JPA로 구현한다(활성 단어만 노출). */
@Repository
@RequiredArgsConstructor
public class NicknameWordRepositoryImpl implements NicknameWordRepository {

  private final NicknameAdjectiveJpaRepository adjectiveRepository;
  private final NicknameNounJpaRepository nounRepository;

  @Override
  public List<String> findActiveAdjectives() {
    return adjectiveRepository.findByActiveTrue().stream()
        .map(NicknameAdjectiveJpaEntity::getWord)
        .toList();
  }

  @Override
  public List<String> findActiveNouns() {
    return nounRepository.findByActiveTrue().stream().map(NicknameNounJpaEntity::getWord).toList();
  }
}
