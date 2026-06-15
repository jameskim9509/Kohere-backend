package com.kohere.gamification.application;

import com.kohere.common.response.PageResponse;
import com.kohere.gamification.application.dto.PointHistoryResponse;
import com.kohere.gamification.application.dto.PointSummaryResponse;
import com.kohere.gamification.application.dto.QuizSubmissionResponse;
import com.kohere.gamification.application.dto.TodayQuizResponse;
import com.kohere.gamification.domain.PointHistoryRepository;
import com.kohere.gamification.domain.QuizRepository;
import com.kohere.gamification.domain.QuizSubmissionRepository;
import com.kohere.gamification.presentation.dto.SubmitQuizRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게이미피케이션(퀴즈·포인트) 유스케이스 조율. 도메인(포트)을 호출하고 흐름만 조율한다. 정답 판정·적립 같은 도메인 규칙은 엔티티/도메인 서비스에
 * 둔다(docs/convention/code-style.md §3-3).
 *
 * <p>의존성은 생성자 주입({@code @RequiredArgsConstructor})으로 받는다(§3-4). 인증 주체(userId)는 SecurityContext에서
 * 가져온다(TODO: 보안 설정 후 연동). 조회·제출은 인증 주체로만 필터링되어 타인 데이터를 다루지 않는다.
 *
 * <p>TODO: 영속 계층(JPA) 도입 시 유스케이스에 트랜잭션 경계({@code @Transactional})를 추가한다(채점·적립은 단일 트랜잭션).
 */
@Service
@RequiredArgsConstructor
public class GamificationService {

  private final QuizRepository quizRepository;
  private final QuizSubmissionRepository quizSubmissionRepository;
  private final PointHistoryRepository pointHistoryRepository;

  public TodayQuizResponse getToday() {
    throw new UnsupportedOperationException("TODO: 오늘의 퀴즈 조회(제출 여부·결과 포함)");
  }

  public QuizSubmissionResponse submit(Long quizId, SubmitQuizRequest request) {
    throw new UnsupportedOperationException("TODO: 정답 제출·즉시 채점·적립(하루 1회 제한)");
  }

  public PointSummaryResponse getSummary() {
    throw new UnsupportedOperationException("TODO: 내 포인트 합계 조회");
  }

  public PageResponse<PointHistoryResponse> getHistories(int page, int size) {
    throw new UnsupportedOperationException("TODO: 내 포인트 적립 내역 조회(오프셋 페이지)");
  }
}
