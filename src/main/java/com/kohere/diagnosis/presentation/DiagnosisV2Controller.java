package com.kohere.diagnosis.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.security.AuthPrincipal;
import com.kohere.diagnosis.application.DiagnosisFlowService;
import com.kohere.diagnosis.application.dto.DiagnosisFlowResponse;
import com.kohere.diagnosis.presentation.dto.AnswerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 서버 주도 진단 REST 컨트롤러(issue #157·ADR-0036). 클라이언트가 {@code step}을 지정하지 않고 {@code POST
 * /api/v2/diagnoses/next} 하나만 호출하면 서버가 다음 질문·확정 시점을 판단한다. v1({@code /api/v1/diagnoses/*})은 그대로
 * 유지된다.
 *
 * <p>입력 바인딩·응답 래핑만 담당하고 로직은 {@link DiagnosisFlowService}에 위임한다. 인증 필수이며 주체(userId)는
 * {@code @AuthenticationPrincipal AuthPrincipal}에서 꺼낸다(ADR-0010).
 *
 * <p>스펙: docs/api/specs/02-diagnosis-recommendation.md (v2) · 시퀀스 US-2-7.
 */
@RestController
@RequestMapping("/api/v2/diagnoses")
@RequiredArgsConstructor
public class DiagnosisV2Controller {

  private final DiagnosisFlowService diagnosisFlowService;

  /** 현재 문항 답(무답 허용)을 적용하고 다음 결과를 결과코드로 반환한다. */
  @PostMapping("/next")
  public ApiResponse<DiagnosisFlowResponse> next(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestBody(required = false) AnswerRequest request) {
    return ApiResponse.success(diagnosisFlowService.next(principal.userId(), request));
  }
}
