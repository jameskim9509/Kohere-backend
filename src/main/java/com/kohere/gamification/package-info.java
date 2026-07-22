/**
 * 게이미피케이션(학습 퀴즈) Bounded Context. 무상태 랜덤 4지선다 학습 퀴즈를 담당한다 — 요청마다 활성 퀴즈 풀에서 무작위 1개를 사용자 언어로 번역해
 * 조회({@code GET /api/v1/quizzes/random})하고, 제출한 보기를 저장된 정답과 대조해 즉시 채점한다({@code POST
 * /api/v1/quizzes/{quizId}/answer}). 제출 기록·포인트는 없다(ADR-0035).
 *
 * <p>두 엔드포인트는 {@code permitAll}이라 <b>비회원(게스트)도 이용할 수 있고 역할 게이트도 없다</b> — 게스트·세입자·임대인 모두
 * 200이다(#181). 신원을 저장하지 않으므로 게스트 세션 식별자를 요구하지도 발급하지도 않는다.
 *
 * <p>도메인 에러 코드 prefix: {@code QUIZ}. 스펙: docs/api/specs/06-gamification.md, 결정: ADR-0035.
 *
 * <p>표시 언어는 <b>회원에 한해</b> {@code user} 공개 API {@code getLanguage}({@code users.lang}, 미설정이면 en)로 동기
 * 취득하고, <b>게스트는 호출 없이 {@code en} 고정</b>이다(ADR-0002 Decision 5·ADR-0029·#181) — 다른 모듈 타입을 직접 import
 * 하지 않고 공개 API로만 협력한다. 퀴즈 콘텐츠는 MongoDB 카탈로그로 둔다(ADR-0005).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Gamification",
    allowedDependencies = {"common", "user :: api"})
package com.kohere.gamification;
