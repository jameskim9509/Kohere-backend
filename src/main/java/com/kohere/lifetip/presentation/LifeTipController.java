package com.kohere.lifetip.presentation;

import com.kohere.common.response.ApiResponse;
import com.kohere.common.security.AuthPrincipal;
import com.kohere.common.security.AuthPrincipals;
import com.kohere.lifetip.application.LifeTipService;
import com.kohere.lifetip.application.dto.TipListResponse;
import com.kohere.lifetip.application.dto.TopicListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생활 팁(주제별 생활 정보) REST 컨트롤러. 입력 바인딩·DTO 변환만 담당하고 비즈니스 로직은 응용 계층에 위임한다(code-style §3-3). 응답은 공통 래퍼로
 * 감싼다.
 *
 * <p>모든 엔드포인트는 <b>인증이 선택</b>이며 역할 제한도 없다 — {@code /api/v1/life-tips/**}는 {@code permitAll}이라 토큰 없이도
 * 호출할 수 있고(#181), 세입자·임대인·비회원(게스트)이 모두 200을 받는다. 표시 언어가 사용자가 고른 {@code users.lang} 기반이 되면서(#141)
 * 온보딩 완료를 요구할 근거가 사라졌고, 게스트 표시 언어를 {@code en} 고정으로 정하면서 로그인 자체에도 의존하지 않게 됐다(US-8).
 *
 * <p>따라서 인증 주체({@code @AuthenticationPrincipal AuthPrincipal}, ADR-0010)는 게스트 요청에서 {@code null}로
 * 주입된다 — {@code userId()}를 직접 역참조하지 않고 {@link AuthPrincipals#userIdOrNull(AuthPrincipal)}로 꺼내
 * nullable {@code Long}(신원 부재)을 응용 계층에 넘긴다.
 *
 * <p>스펙: docs/api/specs/08-life-tips.md.
 */
@RestController
@RequestMapping("/api/v1/life-tips")
@RequiredArgsConstructor
public class LifeTipController {

  private final LifeTipService lifeTipService;

  @GetMapping("/topics")
  public ApiResponse<TopicListResponse> getTopics(
      @AuthenticationPrincipal AuthPrincipal principal) {
    return ApiResponse.success(lifeTipService.getTopics(AuthPrincipals.userIdOrNull(principal)));
  }

  @GetMapping("/topics/{topicCode}/tips")
  public ApiResponse<TipListResponse> getTips(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String topicCode) {
    return ApiResponse.success(
        lifeTipService.getTips(AuthPrincipals.userIdOrNull(principal), topicCode));
  }
}
