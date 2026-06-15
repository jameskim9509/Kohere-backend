package com.kohere.gamification.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.gamification.application.GamificationService;
import com.kohere.gamification.application.dto.QuizSubmissionResponse;
import com.kohere.gamification.application.dto.TodayQuizResponse;
import com.kohere.gamification.presentation.dto.SubmitQuizRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오늘의 퀴즈 REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다(docs/convention/code-style.md §3-3).
 * 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/06-gamification.md.
 */
@RestController
@RequestMapping("/api/v1/quizzes")
@RequiredArgsConstructor
public class QuizController {

  private final GamificationService gamificationService;

  @GetMapping("/today")
  public ApiResponse<TodayQuizResponse> getToday() {
    return ApiResponse.success(gamificationService.getToday());
  }

  @PostMapping("/{quizId}/submission")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<QuizSubmissionResponse> submit(
      @PathVariable Long quizId, @Valid @RequestBody SubmitQuizRequest request) {
    return ApiResponse.success(gamificationService.submit(quizId, request));
  }
}
