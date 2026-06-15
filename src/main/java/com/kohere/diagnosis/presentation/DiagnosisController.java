package com.kohere.diagnosis.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.PageResponse;
import com.kohere.diagnosis.application.DiagnosisService;
import com.kohere.diagnosis.application.dto.DiagnosisCreatedResponse;
import com.kohere.diagnosis.application.dto.DiagnosisResponse;
import com.kohere.diagnosis.application.dto.LatestDiagnosisResponse;
import com.kohere.diagnosis.application.dto.RecommendationResponse;
import com.kohere.diagnosis.presentation.dto.DiagnosisRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 맞춤 진단·매물 추천 REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다(docs/convention/code-style.md
 * §3-3). 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/02-diagnosis-recommendation.md. 모든 엔드포인트는 인증 필수이며 본인 진단만 접근한다(소유권 검증은 응용 계층
 * TODO).
 */
@RestController
@RequestMapping("/api/v1/diagnoses")
@RequiredArgsConstructor
public class DiagnosisController {

  private final DiagnosisService diagnosisService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<DiagnosisCreatedResponse> submit(
      @Valid @RequestBody DiagnosisRequest request) {
    return ApiResponse.success(diagnosisService.submit(request));
  }

  @GetMapping
  public ApiResponse<PageResponse<DiagnosisResponse>> getHistory(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(diagnosisService.getHistory(page, size));
  }

  @GetMapping("/latest")
  public ApiResponse<LatestDiagnosisResponse> getLatest() {
    return ApiResponse.success(diagnosisService.getLatest());
  }

  @GetMapping("/{diagnosisId}")
  public ApiResponse<DiagnosisResponse> getDetail(@PathVariable Long diagnosisId) {
    return ApiResponse.success(diagnosisService.getDetail(diagnosisId));
  }

  @GetMapping("/{diagnosisId}/recommendations")
  public ApiResponse<PageResponse<RecommendationResponse>> getRecommendations(
      @PathVariable Long diagnosisId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(diagnosisService.getRecommendations(diagnosisId, page, size));
  }
}
