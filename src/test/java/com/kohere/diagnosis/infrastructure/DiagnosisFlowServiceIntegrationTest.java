package com.kohere.diagnosis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.kohere.common.exception.InvalidInputException;
import com.kohere.common.response.PageInfo;
import com.kohere.common.response.PageResponse;
import com.kohere.diagnosis.application.DiagnosisAnswerApplier;
import com.kohere.diagnosis.application.DiagnosisCriteriaMapper;
import com.kohere.diagnosis.application.DiagnosisFlowService;
import com.kohere.diagnosis.application.DiagnosisQuestionTranslator;
import com.kohere.diagnosis.application.dto.DiagnosisFlowResponse;
import com.kohere.diagnosis.application.dto.FlowResultCode;
import com.kohere.diagnosis.infrastructure.DiagnosisQuestionDocument.OptionSpec;
import com.kohere.diagnosis.infrastructure.DiagnosisQuestionDocument.SelectSpec;
import com.kohere.diagnosis.presentation.dto.AnswerRequest;
import com.kohere.listing.api.ListingRecommendationService;
import com.kohere.listing.api.RecommendedListingView;
import com.kohere.user.api.UserAccountService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * v2 서버 주도 진단 흐름({@link DiagnosisFlowService}) MongoDB 통합 테스트(issue #157·ADR-0036). 실제 Mongo로 진행 세션
 * 영속·커서 순서·지역 조기 게이트·재질의/종료·자동 확정·NO_MATCH·멱등을 end-to-end 검증한다. cross-module(listing 추천·user 국가)은
 * mock으로 대체한다.
 */
@DataMongoTest
@Testcontainers
@TestPropertySource(properties = "mongock.enabled=false")
@Import({
  DiagnosisFlowService.class,
  DiagnosisAnswerApplier.class,
  DiagnosisCriteriaMapper.class,
  DiagnosisQuestionTranslator.class,
  DiagnosisFlowSessionRepositoryImpl.class,
  DiagnosisRepositoryImpl.class,
  SequenceGenerator.class,
  DiagnosisQuestionCatalogImpl.class
})
class DiagnosisFlowServiceIntegrationTest {

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  @Autowired DiagnosisFlowService flowService;
  @Autowired DiagnosisMongoRepository diagnosisMongoRepository;
  @Autowired DiagnosisQuestionMongoRepository questionMongoRepository;
  @Autowired DiagnosisFlowSessionMongoRepository flowSessionMongoRepository;

  @MockitoBean ListingRecommendationService listingRecommendationService;
  @MockitoBean UserAccountService userAccountService;

  @BeforeEach
  void setUp() {
    diagnosisMongoRepository.deleteAll();
    questionMongoRepository.deleteAll();
    flowSessionMongoRepository.deleteAll();
    seedCatalog();
    given(userAccountService.getLanguage(anyLong())).willReturn("en");
  }

  @Test
  @DisplayName("최초 next(무답)는 ① 지역 질문을 반환한다(클라가 step 미지정)")
  void firstNextReturnsRegionQuestion() {
    DiagnosisFlowResponse res = next(1L, null);
    assertThat(res.resultCode()).isEqualTo(FlowResultCode.NEXT_QUESTION);
    assertThat(res.question().field()).isEqualTo("region");
    assertThat(res.question().step()).isEqualTo(1);
  }

  @Test
  @DisplayName("① 지역에 매물이 있으면 다음(② 목적) 질문으로 진행한다")
  void regionWithMatchProceedsToPurpose() {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(pageOf(view()));
    next(2L, null);
    DiagnosisFlowResponse res = answer(2L, "region", "SEOUL");
    assertThat(res.resultCode()).isEqualTo(FlowResultCode.NEXT_QUESTION);
    assertThat(res.question().field()).isEqualTo("purpose");
  }

  @Test
  @DisplayName("① 지역에 매물이 0건이면 REGION_RETRY(서버 합성 yes/no)를 반환한다")
  void regionNoMatchReturnsRegionRetry() {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());
    next(3L, null);
    DiagnosisFlowResponse res = answer(3L, "region", "BUSAN");
    assertThat(res.resultCode()).isEqualTo(FlowResultCode.REGION_RETRY);
    assertThat(res.question().field()).isEqualTo("regionRetry");
    assertThat(res.question().options()).extracting(o -> o.code()).containsExactly("YES", "NO");
  }

  @Test
  @DisplayName("REGION_RETRY에 '예'면 ① 지역부터 재시작한다")
  void regionRetryYesRestartsFromRegion() {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());
    next(4L, null);
    answer(4L, "region", "BUSAN"); // → REGION_RETRY
    DiagnosisFlowResponse res = answer(4L, "regionRetry", "YES");
    assertThat(res.resultCode()).isEqualTo(FlowResultCode.NEXT_QUESTION);
    assertThat(res.question().field()).isEqualTo("region");
  }

  @Test
  @DisplayName("REGION_RETRY에 '아니오'면 진단을 종료(TERMINATED)하고 세션을 삭제한다")
  void regionRetryNoTerminates() {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());
    next(5L, null);
    answer(5L, "region", "BUSAN"); // → REGION_RETRY
    DiagnosisFlowResponse res = answer(5L, "regionRetry", "NO");
    assertThat(res.resultCode()).isEqualTo(FlowResultCode.TERMINATED);
    assertThat(res.question()).isNull();
    assertThat(flowSessionMongoRepository.findByUserId(5L)).isEmpty();
  }

  @Test
  @DisplayName("6단계를 마치면 서버가 자동 확정하고 COMPLETED(추천+diagnosisId)를 반환한다")
  void fullFlowAutoConfirmsCompleted() {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(pageOf(view()));
    long userId = 6L;
    DiagnosisFlowResponse res = runStudyFlow(userId);

    assertThat(res.resultCode()).isEqualTo(FlowResultCode.COMPLETED);
    assertThat(res.diagnosisId()).isNotNull();
    assertThat(res.recommendation()).isNotNull();
    assertThat(res.recommendation().content()).hasSize(1);
    assertThat(res.recommendation().markers()).hasSize(1);
    // 확정 진단은 v1과 동일한 diagnoses 컬렉션에 저장되고, 진행 세션은 삭제된다.
    assertThat(diagnosisMongoRepository.findById(res.diagnosisId())).isPresent();
    assertThat(flowSessionMongoRepository.findByUserId(userId)).isEmpty();
  }

  @Test
  @DisplayName("자동 확정 후 매칭이 0건이면 NO_MATCH 코드만 반환한다(조정 제안 없음)")
  void fullFlowNoFinalMatchReturnsNoMatch() {
    // 1회차(지역 게이트)는 매칭 있음, 2회차(최종 매칭)는 0건.
    given(listingRecommendationService.recommendByCriteria(any()))
        .willReturn(pageOf(view()))
        .willReturn(emptyPage());
    DiagnosisFlowResponse res = runStudyFlow(7L);

    assertThat(res.resultCode()).isEqualTo(FlowResultCode.NO_MATCH);
    assertThat(res.recommendation()).isNull();
    assertThat(res.diagnosisId()).isNull();
  }

  @Test
  @DisplayName("터미널(TERMINATED) 직후 재-POST는 스테일 답을 무시하고 ① 지역부터 새 흐름을 시작한다(멱등)")
  void terminalRePostStartsFresh() {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());
    next(8L, null);
    answer(8L, "region", "BUSAN");
    answer(8L, "regionRetry", "NO"); // → TERMINATED, 세션 삭제

    DiagnosisFlowResponse res = answer(8L, "arcStatus", "ARC_ISSUED"); // 스테일 답
    assertThat(res.resultCode()).isEqualTo(FlowResultCode.NEXT_QUESTION);
    assertThat(res.question().field()).isEqualTo("region");
  }

  @Test
  @DisplayName("현재 단계와 다른 field를 보내면 INVALID_INPUT(순서 강제)")
  void wrongFieldRejected() {
    next(9L, null); // 기대 field=region
    assertThatThrownBy(() -> answer(9L, "purpose", "STUDY"))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  @DisplayName("REGION_RETRY 상태에서 YES/NO 외 응답은 INVALID_INPUT")
  void regionRetryInvalidCodeRejected() {
    given(listingRecommendationService.recommendByCriteria(any())).willReturn(emptyPage());
    next(10L, null);
    answer(10L, "region", "BUSAN"); // → REGION_RETRY
    assertThatThrownBy(() -> answer(10L, "regionRetry", "MAYBE"))
        .isInstanceOf(InvalidInputException.class);
  }

  // --- helpers ---

  private DiagnosisFlowResponse next(long userId, AnswerRequest request) {
    return flowService.next(userId, request);
  }

  private DiagnosisFlowResponse answer(long userId, String field, String code) {
    return flowService.next(userId, new AnswerRequest(field, code, null, null, null));
  }

  /** ① 지역부터 ⑥ ARC까지 순서대로 답해 마지막 답(자동 확정)의 결과를 반환한다. */
  private DiagnosisFlowResponse runStudyFlow(long userId) {
    next(userId, null);
    answer(userId, "region", "SEOUL");
    answer(userId, "purpose", "STUDY");
    answer(userId, "university", "SNU_CAU_SOONGSIL");
    flowService.next(
        userId, new AnswerRequest("conditions", null, Set.of("FEMALE_ONLY"), null, null));
    flowService.next(userId, new AnswerRequest("monthlyRent", null, null, 200000, 500000));
    return answer(userId, "arcStatus", "ARC_ISSUED");
  }

  private static RecommendedListingView view() {
    return new RecommendedListingView(
        "6858e2000000000000000001",
        "Cozy",
        "GOSHIWON",
        300000,
        450000,
        500000,
        700000,
        "http://img",
        37.5,
        126.9,
        List.of("FEMALE_ONLY"));
  }

  private static PageResponse<RecommendedListingView> emptyPage() {
    return PageResponse.of(List.of(), new PageInfo(0, 20, 0L, 0, false));
  }

  private static PageResponse<RecommendedListingView> pageOf(RecommendedListingView... views) {
    List<RecommendedListingView> content = List.of(views);
    return PageResponse.of(content, new PageInfo(0, 20, content.size(), 1, false));
  }

  private void seedCatalog() {
    saveQuestion(1, "region", "SINGLE", 1, "SEOUL", "Seoul");
    saveQuestion(2, "purpose", "SINGLE", 1, "STUDY", "Study");
    saveQuestion(3, "university", "SINGLE", 1, "SNU_CAU_SOONGSIL", "Seoul National");
    saveQuestion(3, "district", "SINGLE", 1, "GURO_GU", "Guro-gu");
    saveQuestion(4, "conditions", "MULTI", 3, "FEMALE_ONLY", "Female only");
    saveNumberRange(5, "monthlyRent");
    saveQuestion(6, "arcStatus", "SINGLE", 1, "ARC_ISSUED", "ARC issued");
  }

  private void saveQuestion(
      int step, String field, String selectType, int max, String optionCode, String optionLabel) {
    questionMongoRepository.save(
        DiagnosisQuestionDocument.builder()
            .step(step)
            .field(field)
            .active(true)
            .question(Map.of("en", field + " question"))
            .select(SelectSpec.builder().type(selectType).max(max).build())
            .options(
                List.of(
                    OptionSpec.builder().code(optionCode).label(Map.of("en", optionLabel)).build()))
            .build());
  }

  private void saveNumberRange(int step, String field) {
    questionMongoRepository.save(
        DiagnosisQuestionDocument.builder()
            .step(step)
            .field(field)
            .active(true)
            .question(Map.of("en", field + " question"))
            .select(SelectSpec.builder().type("NUMBER_RANGE").max(0).build())
            .options(List.of())
            .build());
  }
}
