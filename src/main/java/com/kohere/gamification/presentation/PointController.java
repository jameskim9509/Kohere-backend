package com.kohere.gamification.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.response.PageResponse;
import com.kohere.gamification.application.GamificationService;
import com.kohere.gamification.application.dto.PointHistoryResponse;
import com.kohere.gamification.application.dto.PointSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 조회 REST 컨트롤러. 인증 주체의 포인트 합계·적립 내역을 조회한다. 입력 검증·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에
 * 위임한다(docs/convention/code-style.md §3-3). 응답은 공통 래퍼로 감싼다.
 *
 * <p>스펙: docs/api/specs/06-gamification.md.
 */
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

  private final GamificationService gamificationService;

  @GetMapping("/summary")
  public ApiResponse<PointSummaryResponse> getSummary() {
    return ApiResponse.success(gamificationService.getSummary());
  }

  @GetMapping("/histories")
  public ApiResponse<PageResponse<PointHistoryResponse>> getHistories(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(gamificationService.getHistories(page, size));
  }
}
