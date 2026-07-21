/**
 * 생활 팁(주제별 생활 정보) Bounded Context. 한국 생활 정보를 주제별로 조회하는 읽기 전용 큐레이션이다 — 주제 목록·주제별 팁(제목·내용·사진) 조회를
 * 담당한다(US-8). 로그인·역할 제한이 없어 세입자·임대인·비회원(게스트)이 모두 조회할 수 있다(#181). 콘텐츠는 운영이 Mongock 시드로 적재하며 발행·구독
 * 도메인 이벤트가 없다.
 *
 * <p>도메인 에러 코드 prefix: {@code LIFE_TIP}. 스펙: docs/api/specs/08-life-tips.md.
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common} 외에 {@code user ::
 * api}의 표시 언어 조회({@code getLanguage})에 의존한다(ADR-0002 Decision 5·ADR-0029, 사용자 선택 표시 언어({@code
 * users.lang}) 번역용 동기 공개 쿼리 — 게스트는 호출하지 않고 {@code en} 고정이다).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Life Tips",
    allowedDependencies = {"common", "user :: api"})
package com.kohere.lifetip;
