package com.kohere.user.infrastructure;

import com.kohere.user.domain.NicknameGenerator;
import com.kohere.user.domain.NicknameWordRepository;
import com.kohere.user.domain.UserRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 닉네임 생성 도메인 서비스 구현. 형용사·사물 풀에서 무작위로 각 1개를 골라 {@code 형용사 + 사물}로 조합하고, {@code users.nickname} 전역 유니크
 * 충돌 시 재조합 재시도(상한 {@link #MAX_RETRIES})한다. 상한 초과 시 숫자 접미사 fallback으로 종료를 보장한다. 동시 생성 경합은 최종적으로
 * {@code users.nickname} UNIQUE 제약이 차단한다(위반 시 재조합은 상위 흐름에서 처리). domain-model §2(닉네임 생성).
 */
@Component
@RequiredArgsConstructor
public class NicknameGeneratorImpl implements NicknameGenerator {

  private static final int MAX_RETRIES = 10;

  private final NicknameWordRepository wordRepository;
  private final UserRepository userRepository;

  @Override
  public String generateUnique() {
    List<String> adjectives = wordRepository.findActiveAdjectives();
    List<String> nouns = wordRepository.findActiveNouns();
    if (adjectives.isEmpty() || nouns.isEmpty()) {
      throw new IllegalStateException("닉네임 단어 풀이 비어 있습니다(시드 확인 필요).");
    }
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < MAX_RETRIES; i++) {
      String candidate = combine(adjectives, nouns, random, "");
      if (!userRepository.existsByNickname(candidate)) {
        return candidate;
      }
    }
    // fallback: 숫자 접미사로 유일성 확보(종료 보장)
    for (int i = 0; i < MAX_RETRIES; i++) {
      String candidate = combine(adjectives, nouns, random, String.valueOf(random.nextInt(10000)));
      if (!userRepository.existsByNickname(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("닉네임 생성에 실패했습니다(재시도·fallback 소진).");
  }

  private static String combine(
      List<String> adjectives, List<String> nouns, ThreadLocalRandom random, String suffix) {
    return adjectives.get(random.nextInt(adjectives.size()))
        + nouns.get(random.nextInt(nouns.size()))
        + suffix;
  }
}
