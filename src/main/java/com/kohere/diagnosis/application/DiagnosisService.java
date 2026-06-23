package com.kohere.diagnosis.application;

import com.kohere.common.response.PageResponse;
import com.kohere.diagnosis.application.dto.DiagnosisCreatedResponse;
import com.kohere.diagnosis.application.dto.DiagnosisResponse;
import com.kohere.diagnosis.application.dto.LatestDiagnosisResponse;
import com.kohere.diagnosis.application.dto.QuestionResponse;
import com.kohere.diagnosis.application.dto.RecommendationResponse;
import com.kohere.diagnosis.domain.DiagnosisRepository;
import com.kohere.diagnosis.presentation.dto.AnswerRequest;
import com.kohere.diagnosis.presentation.dto.DiagnosisRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 맞춤 진단·매물 추천 유스케이스 조율. 도메인(포트)을 호출하고 흐름만 조율한다. 도메인 규칙은 엔티티/도메인 서비스에
 * 둔다(docs/convention/code-style.md §3-3).
 *
 * <p>의존성은 생성자 주입({@code @RequiredArgsConstructor})으로 받는다(§3-4). 인증 주체(userId)는 SecurityContext에서
 * 가져온다(TODO: 보안 설정 후 연동). 단건/추천 조회는 본인 소유만 허용한다(타인 소유는 403 FORBIDDEN — TODO: 소유권 검증 추가).
 *
 * <p>TODO: 영속 계층(JPA) 도입 시 유스케이스에 트랜잭션 경계({@code @Transactional})를 추가한다.
 *
 * <p>스펙: docs/api/specs/02-diagnosis-recommendation.md.
 */
@Service
@RequiredArgsConstructor
public class DiagnosisService {

  private final DiagnosisRepository diagnosisRepository;

  public QuestionResponse getQuestion(int step) {
    throw new UnsupportedOperationException(
        "TODO: 단계별 질문 조회 — diagnosisQuestions 데이터·서버 분기(③ purpose→university/district)·등록국가 번역, #34 Mongo 선행");
  }

  public void submitAnswer(AnswerRequest request) {
    throw new UnsupportedOperationException(
        "TODO: 단계별 답 저장 — in-progress(IN_PROGRESS) 진단에 field별 답 저장·미정의 enum/목적-대학지역 불일치 검증(INVALID_INPUT), #34 Mongo 선행");
  }

  public DiagnosisCreatedResponse submit(DiagnosisRequest request) {
    throw new UnsupportedOperationException("TODO: 진단 제출·저장(재진단 = 새 레코드 생성)");
  }

  public PageResponse<DiagnosisResponse> getHistory(int page, int size) {
    throw new UnsupportedOperationException("TODO: 내 진단 이력 목록(최신순, 오프셋 페이지)");
  }

  public LatestDiagnosisResponse getLatest() {
    throw new UnsupportedOperationException("TODO: 최근 진단 단건(이력 없으면 completed=false)");
  }

  public DiagnosisResponse getDetail(Long diagnosisId) {
    throw new UnsupportedOperationException("TODO: 진단 단건 상세(본인 소유 검증)");
  }

  public PageResponse<RecommendationResponse> getRecommendations(
      Long diagnosisId, int page, int size) {
    throw new UnsupportedOperationException("TODO: 진단 결과 추천 매물·지도 좌표(본인 소유 검증)");
  }
}
