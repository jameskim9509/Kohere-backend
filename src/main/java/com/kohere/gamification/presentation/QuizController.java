package com.kohere.gamification.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.security.AuthPrincipal;
import com.kohere.common.security.AuthPrincipals;
import com.kohere.gamification.application.GamificationService;
import com.kohere.gamification.application.dto.AnswerResultResponse;
import com.kohere.gamification.application.dto.RandomQuizResponse;
import com.kohere.gamification.presentation.dto.AnswerQuizRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 퀴즈 REST 컨트롤러. 입력 바인딩·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다(docs/convention/code-style.md §3-3).
 * 응답은 공통 래퍼로 감싼다.
 *
 * <p>두 엔드포인트 모두 {@code permitAll}이라 <b>로그인 없이(게스트) 호출할 수 있고 역할 게이트도 없다</b> — 게스트·세입자·임대인 모두
 * 200이다(#181). 따라서 인증 주체는 {@code null}일 수 있으므로 {@code principal.userId()}를 직접 역참조하지 않고 {@link
 * AuthPrincipals#userIdOrNull(AuthPrincipal)}로 nullable한 userId를 꺼내 응용 계층에 넘긴다(ADR-0010) — {@code
 * null}이 곧 "게스트"다. 토큰 미전송·위조는 게스트지만, <b>만료 토큰은 보안 필터가 {@code 401 TOKEN_EXPIRED}로 끊는다</b>(강등하지 않음).
 *
 * <p>무상태다 — 랜덤 조회·즉시 채점만 하고 제출 기록·포인트가 없다(ADR-0035). 신원을 저장하지 않으므로 게스트에게 세션 식별자를 요구하지도 발급하지도 않는다.
 *
 * <p>스펙: docs/api/specs/06-gamification.md.
 */
@RestController
@RequestMapping("/api/v1/quizzes")
@RequiredArgsConstructor
public class QuizController {

  private final GamificationService gamificationService;

  @GetMapping("/random")
  public ApiResponse<RandomQuizResponse> getRandom(
      @AuthenticationPrincipal AuthPrincipal principal) {
    return ApiResponse.success(
        gamificationService.getRandomQuiz(AuthPrincipals.userIdOrNull(principal)));
  }

  @PostMapping("/{quizId}/answer")
  public ApiResponse<AnswerResultResponse> answer(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long quizId,
      @Valid @RequestBody AnswerQuizRequest request) {
    return ApiResponse.success(
        gamificationService.gradeAnswer(AuthPrincipals.userIdOrNull(principal), quizId, request));
  }
}
