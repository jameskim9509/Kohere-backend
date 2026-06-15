package com.kohere.report.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.report.application.ReportService;
import com.kohere.report.application.dto.ReasonListResponse;
import com.kohere.report.application.dto.ReportResponse;
import com.kohere.report.presentation.dto.CreateReportRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 콘텐츠 신고 REST 컨트롤러. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다 (docs/convention/code-style.md §3-3).
 * 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/07-reports.md.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;

  @GetMapping("/reasons")
  public ApiResponse<ReasonListResponse> getReasons() {
    return ApiResponse.success(reportService.getReasons());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ReportResponse> createReport(@Valid @RequestBody CreateReportRequest request) {
    return ApiResponse.success(reportService.createReport(request));
  }
}
