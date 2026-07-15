package com.kohere.diagnosis.application;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.common.response.PageResponse;
import com.kohere.diagnosis.application.dto.DiagnosisFlowResponse;
import com.kohere.diagnosis.application.dto.DiagnosisRecommendationView;
import com.kohere.diagnosis.application.dto.QuestionResponse;
import com.kohere.diagnosis.domain.Diagnosis;
import com.kohere.diagnosis.domain.DiagnosisFlowSession;
import com.kohere.diagnosis.domain.DiagnosisFlowSessionRepository;
import com.kohere.diagnosis.domain.DiagnosisFlowStep;
import com.kohere.diagnosis.domain.DiagnosisQuestion;
import com.kohere.diagnosis.domain.DiagnosisQuestionCatalog;
import com.kohere.diagnosis.domain.DiagnosisRepository;
import com.kohere.diagnosis.domain.FlowState;
import com.kohere.diagnosis.presentation.dto.AnswerRequest;
import com.kohere.listing.api.ListingRecommendationService;
import com.kohere.listing.api.RecommendationCriteria;
import com.kohere.listing.api.RecommendedListingView;
import com.kohere.user.api.UserAccountService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * v2 서버 주도 진단 흐름 엔진(issue #157·ADR-0036). 클라이언트가 {@code step}을 지정하지 않고 {@code POST
 * /api/v2/diagnoses/next} 하나만 반복 호출하면, 서버가 진행 위치({@code cursor})로 다음 질문을 결정하고 빌더 완성 시 자동 확정한다. ① 지역
 * 답 직후 매칭 0건이면 재질의({@code REGION_RETRY}) 후 "예"=지역부터 재시작 / "아니오"=종료({@code TERMINATED}).
 *
 * <p>기존 v1({@link DiagnosisService})은 그대로 두고, 답 적용·조건 매핑·문항 번역·③ 분기는 공유 컴포넌트({@link
 * DiagnosisAnswerApplier}·{@link DiagnosisCriteriaMapper}·{@link DiagnosisQuestionTranslator})를
 * 재사용한다. 진행 상태는 v1의 {@code diagnoses}(IN_PROGRESS)와 분리된 {@link DiagnosisFlowSessionRepository}에 담고,
 * 완료 시에만 {@link DiagnosisRepository}로 정본 진단을 저장한다.
 *
 * <p>MongoDB 단일 노드 트랜잭션 미지원이라 {@code @Transactional}을 두지 않는다(연산은 단건 위주).
 */
@Service
@RequiredArgsConstructor
public class DiagnosisFlowService {

  /** ① 지역 존재 확인은 매칭 유무만 필요하므로 1건만 조회한다(size=1, 첫 페이지). */
  private static final int REGION_GATE_SIZE = 1;

  /** 자동 확정 시 인라인으로 담는 추천 첫 페이지 크기(이후 페이지는 v1 recommendations로 조회). */
  private static final int COMPLETED_PAGE_SIZE = 20;

  /** 서버 합성 예외질문("다른 지역?") 문구·라벨의 언어-키 맵(v2는 suggestions 카탈로그를 쓰지 않음). */
  private static final Map<String, String> REGION_RETRY_PROMPT =
      Map.of(
          "en", "There are no rooms in this region yet. Would you like to look in another region?",
          "ko", "현재 지역에는 매물이 없어요. 다른 지역 방을 찾아보시겠어요?",
          "ja", "この地域にはまだ物件がありません。別の地域を探しますか？");

  private static final Map<String, String> YES_LABEL = Map.of("en", "Yes", "ko", "예", "ja", "はい");
  private static final Map<String, String> NO_LABEL = Map.of("en", "No", "ko", "아니오", "ja", "いいえ");

  private final DiagnosisFlowSessionRepository flowSessionRepository;
  private final DiagnosisRepository diagnosisRepository;
  private final DiagnosisQuestionCatalog questionCatalog;
  private final ListingRecommendationService listingRecommendationService;
  private final UserAccountService userAccountService;
  private final DiagnosisAnswerApplier answerApplier;
  private final DiagnosisCriteriaMapper criteriaMapper;
  private final DiagnosisQuestionTranslator questionTranslator;

  /**
   * 현재 문항 답을 적용하고 다음 결과(다음 질문 / 지역 예외질문 / 자동 확정 결과)를 결과코드로 반환한다. 최초·재개 호출은 무답({@code null} 또는 빈
   * field) 허용이며, 터미널 직후 재-POST면 스테일 답을 무시하고 새 흐름을 시작한다(멱등).
   */
  public DiagnosisFlowResponse next(long userId, AnswerRequest request) {
    boolean hasAnswer = request != null && request.field() != null && !request.field().isBlank();

    DiagnosisFlowSession session = flowSessionRepository.findByUserId(userId).orElse(null);
    if (session == null) {
      // 세션 없음(최초 또는 터미널 직후 재-POST) → 스테일 답 무시하고 REGION부터 새 흐름 시작(중복 확정 방지).
      DiagnosisFlowSession started = flowSessionRepository.save(DiagnosisFlowSession.start(userId));
      return computeNext(started);
    }

    if (session.getState() == FlowState.AWAITING_REGION_RETRY) {
      return handleRegionRetry(session, request, hasAnswer);
    }

    // IN_FLOW
    if (!hasAnswer) {
      return computeNext(session); // 재개: 현재 질문 재조회(진행 없음)
    }

    String expectedField = expectedField(session);
    if (!expectedField.equals(request.field())) {
      throw new InvalidInputException("현재 단계의 답이 아닙니다: " + request.field());
    }
    Diagnosis updatedDraft = answerApplier.apply(session.getDraft(), request);
    DiagnosisFlowSession advanced = session.withDraft(updatedDraft).advanceCursor();

    // 방금 ① 지역을 답한 순간(cursor 0→1)만 지역 조기 게이트를 돈다.
    if (advanced.getCursor() == 1 && regionHasNoMatch(advanced.getDraft())) {
      flowSessionRepository.save(advanced.awaitRegionRetry());
      return regionRetryResponse(userId);
    }
    return computeNext(flowSessionRepository.save(advanced));
  }

  // --- 지역 예외질문(REGION_RETRY) 처리 ---

  private DiagnosisFlowResponse handleRegionRetry(
      DiagnosisFlowSession session, AnswerRequest request, boolean hasAnswer) {
    if (!hasAnswer) {
      return regionRetryResponse(session.getUserId()); // 재개: 예외질문 재프롬프트
    }
    if (!"regionRetry".equals(request.field())) {
      throw new InvalidInputException("지역 재질의 응답(regionRetry)만 보낼 수 있습니다: " + request.field());
    }
    String code = request.code();
    if ("YES".equals(code)) {
      return computeNext(flowSessionRepository.save(session.resetToRegion()));
    }
    if ("NO".equals(code)) {
      flowSessionRepository.deleteByUserId(session.getUserId());
      return DiagnosisFlowResponse.terminated();
    }
    throw new InvalidInputException("regionRetry 응답은 YES 또는 NO여야 합니다: " + code);
  }

  // --- 다음 상태 계산(다음 질문 vs 자동 확정) ---

  private DiagnosisFlowResponse computeNext(DiagnosisFlowSession session) {
    if (session.getCursor() >= DiagnosisFlowStep.count()) {
      return autoConfirm(session);
    }
    DiagnosisFlowStep slot = DiagnosisFlowStep.at(session.getCursor());
    String branchField =
        slot == DiagnosisFlowStep.BRANCH
            ? questionTranslator.resolveBranchField(session.getDraft().getPurpose())
            : null;
    List<DiagnosisQuestion> questions = questionCatalog.findByStep(slot.step());
    if (questions.isEmpty()) {
      throw new IllegalStateException("진단 문항 카탈로그가 없습니다: step=" + slot.step());
    }
    DiagnosisQuestion question = questionTranslator.selectQuestion(questions, branchField);
    QuestionResponse translated =
        questionTranslator.translate(question, resolveLanguage(session.getUserId()));
    return DiagnosisFlowResponse.nextQuestion(translated);
  }

  /** 빌더 완성 → 서버가 자동 확정하고 매칭을 계산해 COMPLETED(추천) 또는 NO_MATCH(코드만)를 반환한다(세션 삭제). */
  private DiagnosisFlowResponse autoConfirm(DiagnosisFlowSession session) {
    Diagnosis completed = diagnosisRepository.save(session.getDraft().complete(Instant.now()));
    flowSessionRepository.deleteByUserId(session.getUserId());

    RecommendationCriteria criteria =
        criteriaMapper.toCriteria(completed, 0, COMPLETED_PAGE_SIZE, null);
    PageResponse<RecommendedListingView> result =
        listingRecommendationService.recommendByCriteria(criteria);
    if (result.content().isEmpty()) {
      return DiagnosisFlowResponse.noMatch();
    }
    return DiagnosisFlowResponse.completed(
        completed.getId(), DiagnosisRecommendationView.from(result));
  }

  // --- 헬퍼 ---

  /** 현재 cursor 슬롯이 기대하는 제출 필드(BRANCH는 저장된 purpose로 university/district 택일). */
  private String expectedField(DiagnosisFlowSession session) {
    DiagnosisFlowStep slot = DiagnosisFlowStep.at(session.getCursor());
    if (slot == DiagnosisFlowStep.BRANCH) {
      return questionTranslator.resolveBranchField(session.getDraft().getPurpose());
    }
    return slot.field();
  }

  /** region만 채운 경량 조건으로 매칭 유무를 확인한다(0건이면 true). */
  private boolean regionHasNoMatch(Diagnosis draft) {
    RecommendationCriteria criteria = criteriaMapper.toCriteria(draft, 0, REGION_GATE_SIZE, null);
    return listingRecommendationService.recommendByCriteria(criteria).content().isEmpty();
  }

  private DiagnosisFlowResponse regionRetryResponse(long userId) {
    String language = resolveLanguage(userId);
    QuestionResponse question =
        new QuestionResponse(
            DiagnosisFlowStep.REGION.step(),
            "regionRetry",
            questionTranslator.pickLabel(REGION_RETRY_PROMPT, language),
            new QuestionResponse.Select("SINGLE", 1),
            List.of(
                new QuestionResponse.Option(
                    "YES", questionTranslator.pickLabel(YES_LABEL, language)),
                new QuestionResponse.Option(
                    "NO", questionTranslator.pickLabel(NO_LABEL, language))));
    return DiagnosisFlowResponse.regionRetry(question);
  }

  private String resolveLanguage(long userId) {
    return userAccountService.getLanguage(userId);
  }
}
