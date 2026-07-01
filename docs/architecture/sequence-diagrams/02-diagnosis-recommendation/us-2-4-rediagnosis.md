# US-2-4 — 재진단(새 진단 생성)

> 모듈: 맞춤 진단 & 매물 추천 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/02-diagnosis-recommendation.md)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant DIAG as diagnosis 모듈
    participant DB as MongoDB

    U->>C: 조건 변경 후 재진단 제출
    C->>SEC: POST /api/v1/diagnoses<br/>변경된 region/purpose/university(대학 그룹 선택)|district(목적 분기)/<br/>conditions/월세 범위(monthlyRentMin·monthlyRentMax)/arcStatus<br/>Authorization: Bearer accessToken
    Note over SEC: JWT 검증 (서명·만료·클레임)
    SEC->>DIAG: 인증된 요청 전달 (userId)
    Note over DIAG: 입력 재검증<br/>항상 새 diagnosis 레코드 생성<br/>(기존 진단 덮어쓰지 않음)
    DIAG->>DB: 새 diagnosis 저장
    DB-->>DIAG: 저장 완료(새 diagnosisId)
    DIAG-->>C: 201 Created<br/>Location: /api/v1/diagnoses/{diagnosisId}<br/>새 diagnosisId, status COMPLETED
    C-->>U: 새 진단 결과로 이동<br/>(기존 이력 보존)
```

## 흐름 요약

- 재진단도 US-2-1과 동일한 `POST /api/v1/diagnoses`이며, 공통 보안 필터(SEC)가 컨트롤러 앞단에서 JWT를 검증한 뒤 diagnosis 모듈로 전달하고 diagnosis 모듈이 항상 새 diagnosis를 MongoDB에 저장해 새 `diagnosisId`를 발급하며 기존 진단을 덮어쓰지 않고 이력을 보존한다(`201 Created`).
- 재진단 본문도 US-2-1과 동일한 입력 검증 규칙을 적용한다(위반 시 `400 INVALID_INPUT` + `errors[]`).
