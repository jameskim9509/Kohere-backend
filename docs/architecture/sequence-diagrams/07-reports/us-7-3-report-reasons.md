# US-7-3 — 신고 사유 목록(enum 메타) 조회

> 모듈: 신고 처리 · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/07-reports.md)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant REPORT as report 모듈

    U->>C: 신고 화면 진입
    C->>REPORT: GET /api/v1/reports/reasons<br/>(인증 불필요)
    Note over REPORT: 고정·소규모 enum 카탈로그<br/>페이지네이션 미적용
    REPORT-->>C: 200 OK<br/>reasons[]: { code, label }<br/>SPAM/ABUSE/SEXUAL_CONTENT/<br/>EXTERNAL_CONTACT/FALSE_INFO/ETC
    C-->>U: 신고 사유 선택지 표시
```

## 흐름 요약

- 앱은 신고 화면에서 인증 없이 `GET /api/v1/reports/reasons`를 `report 모듈`에 호출한다.
- `report 모듈`은 고정·소규모 enum 카탈로그를 페이지네이션 없이 `200 OK`로 한 번에 반환한다.
- 응답의 `reasons[]`(`code`/`label`)로 앱이 서버와 동일한 신고 사유 선택지를 구성한다.
