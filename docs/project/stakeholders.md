# Stakeholders

## 목적

이 문서는 프로젝트 이해관계자의 역할과 책임, 의사결정 경로를 정리한다.
"누구에게 물어봐야 하는가", "이 결정은 누가 내리는가"를 빠르게 찾기 위함이다.

> 예시(Spring Boot 기준): 아래 담당/연락 채널은 모두 가짜 예시 값이다.
> 실제 이름/연락처/채널은 프로젝트 확정 후 교체한다. 실명·실주소·시크릿은 적지 않는다.

---

## 1. 이해관계자 목록

| 역할 | 담당(예시) | 책임 | 연락 채널(예시) |
| --- | --- | --- | --- |
| 제품 책임자(PO) | `po-alias` | 범위/우선순위 결정, KPI 정의 | `#meetup-product` |
| 프로젝트 매니저(PM) | `pm-alias` | 일정/리스크 관리, 마일스톤 추적 | `#meetup-pm` |
| 백엔드 리드 | `be-lead` | 아키텍처/기술 결정, 코드 리뷰 기준 | `#meetup-backend` |
| 백엔드 개발자 | `be-dev-*` | API/도메인/DB 구현 및 테스트 | `#meetup-backend` |
| 프론트엔드 리드 | `fe-lead` | 클라이언트 연동, API contract 합의 | `#meetup-frontend` |
| QA | `qa-alias` | 테스트 계획/검증, 품질 게이트 | `#meetup-qa` |
| DevOps/SRE | `sre-alias` | 배포/모니터링/장애 대응 | `#meetup-ops` |
| 보안 담당 | `sec-alias` | 보안 검토, 시크릿 정책 | `#meetup-security` |
| 디자이너 | `design-alias` | UX/플로우 정의 | `#meetup-design` |

> 연락 채널은 예시 채널명이다. 실제 메신저/이메일 주소는 별도 비공개 위키에 둔다(이 저장소에 적지 않는다).

---

## 2. RACI 매트릭스

R = Responsible(실행), A = Accountable(최종 책임/승인), C = Consulted(자문), I = Informed(공유)

| 활동 | PO | PM | BE 리드 | BE 개발 | FE 리드 | QA | SRE | 보안 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 요구사항/범위 확정 | A | R | C | I | C | I | I | I |
| 기술 스택 결정(ADR) | C | I | A/R | C | C | I | C | C |
| API contract 합의 | C | I | A | R | C | C | I | I |
| DB 스키마 설계 | I | I | A | R | I | I | C | C |
| 기능 구현 | I | I | A | R | I | I | I | I |
| 테스트/품질 게이트 | I | C | C | R | I | A | I | I |
| 보안 검토 | I | I | C | C | I | I | C | A/R |
| 배포 | I | C | A | C | I | I | R | I |
| 장애 대응 | I | I | C | C | I | I | A/R | C |

> 규칙: 각 행에 **A는 정확히 1명**만 둔다. R은 여러 명일 수 있다.

---

## 3. 의사결정 / 에스컬레이션 경로

```
구현 세부 결정        →  BE 개발자가 결정, BE 리드에게 공유
아키텍처/스택 결정    →  BE 리드가 ADR 작성, PO/보안 자문 후 확정
범위/우선순위 변경    →  PM 정리 → PO 승인
배포 중 장애          →  SRE 1차 대응 → BE 리드 호출 → 필요 시 PO 보고
보안 이슈             →  보안 담당 즉시 호출(에스컬레이션 최우선)
```

- 기술 결정은 [adr/README](../adr/README.md)에 기록한다.
- 장애 대응 상세 절차는 [incident-response](../operations/incident-response.md)를 따른다.

## 관련 문서

- [project-brief](project-brief.md) — 목적/범위/일정
- [glossary](glossary.md) — 용어 정의
- [security-policy](../security/security-policy.md) — 보안 책임
- [runbook](../operations/runbook.md) — 운영 대응
