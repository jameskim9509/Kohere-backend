# Documentation Index

## 목적

이 문서는 `docs/` 전체의 목차이자 네비게이션이다. "어떤 주제를 어디서 보는가"를 한눈에 안내한다.
처음 합류한 팀원은 이 페이지에서 시작해 필요한 영역으로 이동한다.

> 안내: 이 저장소는 백엔드 base repository이며, 일부 예시 문서는 **예시 스택(Spring Boot 3.x / Java 17 / Gradle / Spring Data JPA / PostgreSQL / Flyway / JUnit5 + Mockito + Testcontainers / Spring Security(JWT))** 기준으로 작성되어 있다. 실제 프로젝트 스택이 확정되면 각 문서의 값을 교체한다. 기술 스택 확정 전 규칙은 [CLAUDE.md](../CLAUDE.md)와 [.claude/rules/tech-stack.md](../.claude/rules/tech-stack.md)를 따른다.

---

## 빠른 시작 (Reading Path)

| 나는 누구인가 | 먼저 볼 문서 |
| --- | --- |
| 처음 합류한 백엔드 개발자 | [project-brief](project/project-brief.md) → [system-overview](architecture/system-overview.md) → [code-style](convention/code-style.md) |
| API를 만드는 사람 | [api-design-guide](api/api-design-guide.md) → [error-response-guide](api/error-response-guide.md) → [acceptance-criteria-template](requirements/acceptance-criteria-template.md) |
| DB/마이그레이션 담당 | [database-design](database/database-design.md) → [migration-policy](database/migration-policy.md) → [transaction-policy](database/transaction-policy.md) |
| 운영/배포 담당 | [deployment-guide](operations/deployment-guide.md) → [runbook](operations/runbook.md) → [incident-response](operations/incident-response.md) |
| 기술 결정을 남기는 사람 | [adr/README](adr/README.md) → [0000-adr-template](adr/0000-adr-template.md) |

---

## 폴더별 안내

### project — 프로젝트 개요/맥락

| 문서 | 설명 |
| --- | --- |
| [project-brief](project/project-brief.md) | 프로젝트 목적, 범위(In/Out of scope), 핵심 기능, KPI, 마일스톤 |

### requirements — 요구사항 정의

| 문서 | 설명 |
| --- | --- |
| [user-story-template](requirements/user-story-template.md) | 사용자 스토리 작성 템플릿 |
| [acceptance-criteria-template](requirements/acceptance-criteria-template.md) | 인수 조건(Given/When/Then) 템플릿 |
| [non-functional-requirements](requirements/non-functional-requirements.md) | 성능/가용성/보안 등 비기능 요구사항 |

### convention — 협업 컨벤션

| 문서 | 설명 |
| --- | --- |
| [code-style](convention/code-style.md) | 코드 스타일/네이밍 규칙 |
| [branch-convention](convention/branch-convention.md) | 브랜치 네이밍 전략 |
| [commit-convention](convention/commit-convention.md) | 커밋 메시지 규칙(Conventional Commits) |
| [pr-convention](convention/pr-convention.md) | PR 작성/리뷰 규칙 |
| [documentation-convention](convention/documentation-convention.md) | 문서 작성 규칙 |

### api — API 설계

| 문서 | 설명 |
| --- | --- |
| [api-design-guide](api/api-design-guide.md) | REST API 설계 가이드 |
| [error-response-guide](api/error-response-guide.md) | 에러 응답 표준 포맷 |
| [versioning-policy](api/versioning-policy.md) | API 버저닝/하위 호환 정책 |

### architecture — 아키텍처

| 문서 | 설명 |
| --- | --- |
| [system-overview](architecture/system-overview.md) | 시스템 전체 구성도/컴포넌트 |
| [backend-architecture](architecture/backend-architecture.md) | 계층 분리/책임 경계 |
| [module-boundary](architecture/module-boundary.md) | 모듈 경계와 의존 방향 |
| [external-integration](architecture/external-integration.md) | 외부 시스템 연동 패턴 |
| [observability](architecture/observability.md) | 로그/메트릭/추적 기준 |

### database — 데이터베이스

| 문서 | 설명 |
| --- | --- |
| [database-design](database/database-design.md) | 스키마/ERD 설계 |
| [migration-policy](database/migration-policy.md) | 마이그레이션 정책(Flyway 등) |
| [transaction-policy](database/transaction-policy.md) | 트랜잭션 경계 정책 |

### testing — 테스트

| 문서 | 설명 |
| --- | --- |
| [testing-strategy](testing/testing-strategy.md) | 테스트 전략 + 단위/통합/E2E/데이터 작성 가이드 |

### operations — 운영

| 문서 | 설명 |
| --- | --- |
| [deployment-guide](operations/deployment-guide.md) | 배포 + 롤백 절차 |
| [runbook](operations/runbook.md) | 운영 런북 |
| [incident-response](operations/incident-response.md) | 장애 대응 프로세스 |

### security — 보안

| 문서 | 설명 |
| --- | --- |
| [security-policy](security/security-policy.md) | 보안 정책 개요 |
| [access-control](security/access-control.md) | 인증/인가/권한 경계 |

### adr — 아키텍처 결정 기록

| 문서 | 설명 |
| --- | --- |
| [adr/README](adr/README.md) | ADR 인덱스와 작성 방법 |
| [0000-adr-template](adr/0000-adr-template.md) | ADR 작성 템플릿 |

---

## 문서를 추가/수정할 때

- 새 문서를 만들면 위 표에 한 줄 설명과 상대링크를 추가한다.
- 기술 결정이 바뀌면 [adr/README](adr/README.md)에 ADR을 추가하고 관련 문서를 갱신한다.
- 문서 작성 규칙은 [documentation-convention](convention/documentation-convention.md)을 따른다.
- 코드/정책 변경 시 영향받는 문서를 함께 갱신한다([CLAUDE.md](../CLAUDE.md) "How To Work" 참고).
