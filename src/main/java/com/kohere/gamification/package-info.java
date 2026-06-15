/**
 * 게이미피케이션(퀴즈·포인트) Bounded Context. 오늘의 퀴즈 조회/제출(즉시 채점·적립)과 포인트 합계·적립 내역 조회를 담당한다.
 *
 * <p>도메인 에러 코드 prefix: {@code QUIZ}, {@code POINT}. 스펙: docs/api/specs/06-gamification.md.
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}에만 의존한다. TODO: 정답 적립으로
 * 사용자 포인트가 바뀌면 다른 모듈(user 등)이 필요로 할 수 있는데, 이때는 직접 import 대신 공개 API·이벤트로 연결한다(다른 모듈 타입 import 금지).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Gamification",
    allowedDependencies = {"common"})
package com.kohere.gamification;
