package com.kohere.user.presentation;

import com.kohere.common.security.AuthPrincipal;
import com.kohere.common.security.RefreshTokenCookies;
import com.kohere.user.application.UserService;
import com.kohere.user.application.dto.UserProfileResponse;
import com.kohere.user.presentation.dto.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 프로필·계정 REST 컨트롤러(/api/v1/users/me). 인증 주체(userId)는 공통 보안 필터가 주입한 {@link AuthPrincipal}에서 받는다.
 * 보호 경로 인가(ROLE_USER, PENDING 차단)는 보안 계층이 담당한다(ADR-0010). 도메인 DTO만 반환하고, 공통 래퍼는 {@link
 * com.kohere.common.response.ApiResponseWrapper}가 자동 적용한다(ADR-0013).
 *
 * <p><b>탈퇴 핸들러 하나만 서블릿 응답을 직접 만진다</b> — 세션을 끊는 응답이라 브라우저에 남은 refresh 쿠키까지 지워야 한다(ADR-0048). 근거는
 * {@link #withdraw} 참조.
 *
 * <p>스펙: docs/api/specs/01-auth-onboarding.md §8~10.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final RefreshTokenCookies refreshTokenCookies;

  @GetMapping
  public UserProfileResponse getMyProfile(@AuthenticationPrincipal AuthPrincipal principal) {
    return userService.getMyProfile(principal.userId());
  }

  @PatchMapping
  public UserProfileResponse updateMyProfile(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request) {
    return userService.updateMyProfile(principal.userId(), request);
  }

  /**
   * 회원 탈퇴. 서버 쪽 정리(WITHDRAWN 전이·PII 익명화·자격증명 삭제·refresh 일괄 무효화)는 {@link UserService}와 그 이벤트를 받는
   * auth의 {@code UserWithdrawnEventListener}가 끝내고, 이 핸들러는 <b>브라우저에 남은 refresh 쿠키를 지우는 것</b> 하나를
   * 더한다(ADR-0048).
   *
   * <p><b>보안 구멍을 막는 게 아니라 잔여물을 치우는 것이다</b> — 토큰은 이미 서버에서 전부 {@code REVOKED}라 남은 쿠키로 할 수 있는 일은 없다.
   * 다만 지우지 않으면 죽은 쿠키가 최대 14일(refresh TTL)을 브라우저에 머물며, 재발급을 한 번이라도 재시도하는 화면이 「탈퇴했는데 왜 401인가」라는 설명
   * 불가능한 응답을 받는다. 로그아웃이 같은 이유로 삭제 쿠키를 내리고 있으므로(§7) 탈퇴도 맞춘다 — 세션을 끊는 두 경로의 동작이 갈릴 이유가 없다.
   *
   * <p><b>로그아웃과 달리 조건 없이 내린다.</b> 로그아웃은 「요청이 쿠키로 왔을 때만」 삭제 쿠키를 붙이지만, 그 판정을 여기 옮기면 <b>항상 거짓</b>이다 —
   * 쿠키 {@code Path}가 {@code /api/v1/auth}라 브라우저가 이 요청({@code /api/v1/users/me})에는 refresh 쿠키를 아예 싣지
   * 않는다(ADR-0048 §2에서 경로를 좁힌 것의 대가다). 즉 요청만 봐서는 쿠키 보유 여부를 알 방법이 없으니 무조건 내린다. {@code Set-Cookie}의
   * {@code Path}는 요청 경로와 무관하게 지정할 수 있어({@code Domain}과 다르다) 다른 경로의 응답으로 {@code /api/v1/auth} 쿠키를
   * 지우는 것은 정상 동작이다.
   *
   * <p><b>쿠키가 없던 앱 클라이언트에도 무해하다</b> — 앱은 refresh를 본문으로 주고받아 이 쿠키를 가진 적이 없고, {@code Max-Age=0}은 「지금
   * 만료」라 쿠키 저장소를 쓰는 클라이언트도 지울 것이 없어 아무 일도 일어나지 않는다. 본문·status·에러 계약은 그대로이고 응답 헤더가 하나 늘 뿐이라 하위 호환이다.
   * {@code userType}으로 갈라 임대인에게만 내리는 선택지도 있으나 택하지 않는다 — <b>계정 유형은 클라이언트 채널이 아니다</b>(임대인이 앱에서 탈퇴할
   * 수도, 연동된 계정이 두 채널을 다 가질 수도 있다). 전송 채널 하나를 정하려고 컨트롤러가 계정 상태를 되묻는 것도 방향이 거꾸로다.
   *
   * <p><b>왜 auth가 아니라 여기인가</b> — {@link RefreshTokenCookies}는 {@code auth}가 아니라 공유 커널({@code
   * common.security})에 있다. 쿠키는 도메인 규칙이 아니라 HTTP 전송 수단이라 {@code JwtTokenService}와 같은 자리에 두기로 한
   * 것이고({@link com.kohere.common.security.RefreshCookieProperties} 참조), {@code user}의 허용 의존은 {@code
   * {"common"}}이며 {@code common}은 OPEN 모듈이라 경계상 문제가 없다. 이 컨트롤러는 이미 같은 패키지의 {@link AuthPrincipal}을
   * 쓰고 있어 새로 생기는 의존도 없다.
   *
   * <p>대안 ① <b>auth의 {@code UserWithdrawnEventListener}가 내린다</b>: 이벤트가 같은 요청 스레드에서 동기 발행되므로 {@code
   * RequestContextHolder}로 응답 객체에 닿을 수는 있다 — 불가능해서 버리는 게 아니다. 그래도 택하지 않는 이유가 셋이다. (ㄱ) 응용 계층이 서블릿
   * 타입을 만지게 되어 계층 규칙이 깨진다({@link RefreshTokenCookies} javadoc이 "쿠키를 실제로 내리는 것은 프레젠테이션 계층"이라고 못박아 둔
   * 그 지점이다). (ㄴ) 그 리스너의 javadoc이 스스로 예고하듯 언젠가 {@code @ApplicationModuleListener}(비동기)로 바뀌면 스레드에 묶인
   * 요청이 사라져 <b>삭제 쿠키가 조용히 증발</b>한다 — 컴파일도 되고 탈퇴도 성공하니 아무도 모른다. (ㄷ) 리스너는 탈퇴 트랜잭션 <b>안</b>이라 이후 롤백되면
   * 일어나지 않은 탈퇴의 삭제 쿠키가 나간다. 여기서는 서비스가 반환한 뒤 = 커밋된 뒤에 헤더를 쓰므로 그 창이 없다.
   *
   * <p>대안 ② <b>「세션을 무효화한 응답」 전반을 웹 계층(필터·인터셉터)에서 처리한다</b>: 대상을 무엇으로 판별하느냐가 걸린다. 경로·메서드로 매칭하면 라우팅
   * 지식이 컨트롤러 밖으로 새어 경로를 버전업할 때 조용히 어긋나고, 서비스가 심는 요청 속성으로 판별하면 대안 ①의 암묵적 상태에 한 겹을 더 얹은 것에 지나지 않는다.
   * 게다가 대상은 로그아웃·탈퇴 둘뿐이고 <b>규칙이 서로 다르다</b>(로그아웃은 채널 조건부, 탈퇴는 무조건) — 공통화해도 결국 엔드포인트별 분기가 남는다. 그래서 각
   * 컨트롤러가 자기 응답을 책임진다.
   */
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void withdraw(
      @AuthenticationPrincipal AuthPrincipal principal, HttpServletResponse httpResponse) {
    userService.withdraw(principal.userId());
    httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookies.delete().toString());
  }
}
