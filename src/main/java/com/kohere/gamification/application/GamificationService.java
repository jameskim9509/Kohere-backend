package com.kohere.gamification.application;

import com.kohere.gamification.application.dto.AnswerResultResponse;
import com.kohere.gamification.application.dto.RandomQuizResponse;
import com.kohere.gamification.domain.ChoiceKey;
import com.kohere.gamification.domain.Quiz;
import com.kohere.gamification.domain.QuizNotFoundException;
import com.kohere.gamification.domain.QuizRepository;
import com.kohere.gamification.presentation.dto.AnswerQuizRequest;
import com.kohere.user.api.UserAccountService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게이미피케이션(학습 퀴즈) 유스케이스. 무상태 — 요청마다 활성 퀴즈 풀에서 무작위 1개를 사용자 언어로 번역해 조회하고, 제출한 보기를 저장된 정답과 대조해 즉시
 * 채점한다(제출 기록·포인트 없음, ADR-0035).
 *
 * <p><b>역할 게이트가 없다</b>(#181) — 비회원(게스트)·세입자·임대인 누구나 호출할 수 있다. 퀴즈는 무상태라 신원을 저장하지 않으므로 호출자에 따라 갈리는 것은
 * <b>표시 언어뿐</b>이다.
 *
 * <p>표시 언어는 회원에 한해 {@code user} 공개 query {@code getLanguage}로 취득하며({@code users.lang}, 미설정이면 en —
 * #141), <b>게스트({@code userId == null})는 {@code getLanguage}를 호출하지 않고 {@code en} 고정</b>이다 — {@code
 * users} 행이 없어 호출 자체가 {@code 404 USER_NOT_FOUND}가 되기 때문이다(#181). 해당 언어 번역이 없으면 영어({@code en})로
 * 폴백한다(ADR-0029). 보기 키 A~D는 언어와 무관하고 채점은 키로 수행한다. 의존성은 생성자 주입으로 받는다(§3-4).
 */
@Service
@RequiredArgsConstructor
public class GamificationService {

  /** 표시 문자열 언어-키 맵에서 사용자 언어 값이 없을 때의 폴백 언어이자, 게스트의 고정 표시 언어(ADR-0029·#181). */
  private static final String DEFAULT_LANGUAGE = "en";

  private final QuizRepository quizRepository;
  private final UserAccountService userAccountService;

  /**
   * 활성 퀴즈 풀에서 무작위 1개를 사용자 언어로 번역해 조회한다(정답·해설 제외).
   *
   * @param userId 회원이면 userId, 비회원(게스트)이면 {@code null} — 게스트는 표시 언어가 {@code en} 고정이다
   */
  public RandomQuizResponse getRandomQuiz(Long userId) {
    Quiz quiz = quizRepository.findRandomActive().orElseThrow(QuizNotFoundException::new);
    String language = resolveLanguage(userId);
    List<RandomQuizResponse.Choice> choices =
        quiz.choices().stream()
            .map(c -> new RandomQuizResponse.Choice(c.key().name(), pickLabel(c.text(), language)))
            .toList();
    return new RandomQuizResponse(quiz.id(), pickLabel(quiz.question(), language), choices);
  }

  /**
   * 제출한 보기를 저장된 정답과 대조해 채점한다(무상태). 정답·오답 모두 해설(번역)을, 오답이면 정답 키도 함께 반환한다.
   *
   * @param userId 회원이면 userId, 비회원(게스트)이면 {@code null} — 게스트는 해설 언어가 {@code en} 고정이다
   */
  public AnswerResultResponse gradeAnswer(Long userId, long quizId, AnswerQuizRequest request) {
    Quiz quiz = quizRepository.findById(quizId).orElseThrow(QuizNotFoundException::new);
    ChoiceKey selected = ChoiceKey.valueOf(request.selectedChoice());
    String explanation = pickLabel(quiz.explanation(), resolveLanguage(userId));
    if (quiz.isCorrect(selected)) {
      return new AnswerResultResponse(quiz.id(), selected.name(), true, null, explanation);
    }
    return new AnswerResultResponse(
        quiz.id(), selected.name(), false, quiz.correctChoice().name(), explanation);
  }

  /**
   * 표시 언어를 정한다. 게스트({@code userId == null})는 {@code user} 모듈을 조회하지 않고 {@code en}으로 고정한다 — 게스트는
   * {@code users} 행이 없어 조회하면 {@code 404}가 되므로, 기본값을 주는 것이 아니라 <b>호출 자체를 피하는 것</b>이 요점이다(#181).
   */
  private String resolveLanguage(Long userId) {
    return userId == null ? DEFAULT_LANGUAGE : userAccountService.getLanguage(userId);
  }

  private static String pickLabel(Map<String, String> labels, String language) {
    if (labels == null) {
      return null;
    }
    String value = labels.get(language);
    if (value != null) {
      return value;
    }
    return labels.getOrDefault(DEFAULT_LANGUAGE, labels.values().stream().findFirst().orElse(null));
  }
}
