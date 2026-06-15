/**
 * 공유 커널(Shared Kernel) — OPEN 모듈. 모든 모듈이 자유롭게 사용하는 공통 응답 래퍼, 에러 코드 카탈로그, 비즈니스 예외 기반 타입, 전역 예외 핸들러,
 * 페이지네이션 응답 타입을 제공한다.
 *
 * <p>OPEN 모듈이므로 하위 패키지가 internal로 숨겨지지 않고 다른 모듈에서 import할 수 있다. 경계 규칙은
 * docs/convention/code-style.md §3-1, 에러 응답 규약은 docs/api/error-response-guide.md를 따른다.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Common",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.kohere.common;
