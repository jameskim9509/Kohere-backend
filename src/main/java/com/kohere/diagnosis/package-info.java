/**
 * 맞춤 진단·매물 추천 Bounded Context. 5단계 진단(지역/입국 목적/주거 환경 조건/월 예산/ARC 발급 여부) 제출·저장, 진단 이력·최근 진단·단건 상세
 * 조회, 진단 결과에 따른 추천 매물·지도 좌표 조회를 담당한다.
 *
 * <p>도메인 에러 코드 prefix: {@code DIAGNOSIS}. 스펙: docs/api/specs/02-diagnosis-recommendation.md.
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}에만 의존한다.
 *
 * <p>TODO: 추천 매물 결과는 매물 탐색(listing) 도메인의 매물 데이터를 재사용해야 한다. 모듈 간 직접 import 대신 공개 API·이벤트로 연결한다(현재는
 * 자체 RecommendationResponse 스켈레톤으로 둔다).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Diagnosis",
    allowedDependencies = {"common"})
package com.kohere.diagnosis;
