# Backend Base Repository Template

아직 어떤 백엔드 서비스를 만들지 정하지 않은 상태에서 사용할 수 있는 **backend base repository**입니다.

이 저장소는 다음을 미리 갖춰두는 것을 목표로 합니다.

- 백엔드 프로젝트 공통 폴더 구조
- GitHub Issue/PR/CI 기본 템플릿
- 컨벤션/설계/테스트/운영/보안 문서 템플릿
- Claude Code 기반 AI Agent 활용 구조
- 장기 작업을 위한 harness/progress/task-board 구조

> **참고:** 기술 스택(언어·프레임워크·DB·배포 방식)은 아직 미정입니다. Spring Boot, NestJS, FastAPI, Go, Django 등으로 확장될 수 있으며, 스택이 확정되기 전까지 임의로 프로젝트를 초기화하지 않습니다.

## 사용 방법

```bash
git clone <this-template-repository> <project-name>
cd <project-name>
cp CLAUDE.local.example.md CLAUDE.local.md
bash scripts/setup.sh
claude
```

## 프로젝트가 정해진 뒤 먼저 할 일

1. [docs/project/project-brief.md](docs/project/project-brief.md) 작성
2. [docs/requirements/non-functional-requirements.md](docs/requirements/non-functional-requirements.md) 갱신
3. [docs/architecture/system-overview.md](docs/architecture/system-overview.md) 갱신
4. [.claude/rules/tech-stack.md](.claude/rules/tech-stack.md) 갱신
5. [scripts/quality/check.sh](scripts/quality/check.sh)를 실제 백엔드 스택에 맞게 수정
6. 첫 ADR 작성 ([docs/adr/0000-adr-template.md](docs/adr/0000-adr-template.md) 복사)

## 핵심 디렉터리 한눈에 보기

```text
.github/     GitHub 협업 템플릿과 Actions(CI/문서/AI 리뷰)
.claude/     Claude Code 설정, rules, skills, agents, harness prompt
docs/        사람이 읽는 문서(컨벤션·설계·테스트·운영·보안·ADR)
harness/     장기 작업 진행 상태와 인수인계
scripts/     품질 검사와 Claude hooks
src/         실제 백엔드 코드 위치(main/test)
```

---

# 폴더·파일 설명

아래는 저장소에 포함된 모든 폴더와 파일이 **무엇이고 왜 존재하는지**에 대한 설명입니다.

> 📌 **`docs/` 하위 문서는 "재사용 템플릿 + 구체 예시"로 채워져 있습니다.** 예시는 가상 도메인(모임/일정 공유 "Meetup")과 **Spring Boot 3.x / Java 17 / JPA / PostgreSQL / JUnit5** 기준으로 작성됐습니다. 이는 어디까지나 **예시**이므로, 프로젝트·스택이 확정되면 각 문서 상단 안내에 따라 실제 값으로 교체해 사용합니다. (스택 변경 시 구조·체크리스트는 그대로 두고 코드 예시만 교체)

## 1. 루트 메타/설정 파일

프로젝트의 초기 설정, 협업 규칙, 라이선스, 개발 환경 구성을 정의하는 최상위 메타데이터입니다.

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [README.md](README.md) | 프로젝트 소개 및 시작 가이드 | 기술 스택 미정 상태의 백엔드 base repository임을 설명하고, 디렉터리 구조·초기 설정 방법·확정 후 먼저 할 일과 이 폴더/파일 설명을 담는다. |
| [CLAUDE.md](CLAUDE.md) | Claude Code 핵심 작업 지침 | 프로젝트가 미정인 상태에서 Claude가 따를 기본 전제, 작업 순서, 파일 관리 규칙, 백엔드 원칙, 안전 규칙을 정의한다. |
| [CLAUDE.local.example.md](CLAUDE.local.example.md) | 개인 로컬 Claude 설정 예시 | 답변 언어·스타일, 로컬 환경 정보, 개인용 명령 등을 **커밋하지 않고** 관리하기 위한 예시 템플릿이다. `CLAUDE.local.md`로 복사해 사용한다. |
| [.gitignore](.gitignore) | Git 제외 설정 | `CLAUDE.local.md`, `.env`, 시크릿(키·인증서), 빌드 산출물, 의존성, IDE/OS 파일 등을 커밋에서 제외한다. |
| [.editorconfig](.editorconfig) | 에디터 스타일 표준화 | UTF-8 인코딩, LF 줄바꿈, 최종 줄바꿈 삽입, 공백 들여쓰기 등 IDE 간 일관성을 위한 설정이다. |
| [.env.example](.env.example) | 환경 변수 템플릿 | 프로젝트에 필요한 환경 변수 예시를 제공해 개발자가 로컬 `.env`를 작성하도록 돕는다. (실제 `.env`는 커밋 금지) |
| [.mcp.example.json](.mcp.example.json) | MCP 서버 설정 예시 | Claude Code가 사용할 MCP 서버(filesystem, github)를 `npx`로 설치하고 토큰 환경변수를 주입하는 설정 예시다. |

## 2. `.github/` — GitHub 협업·CI 템플릿

PR/이슈 템플릿과 GitHub Actions 자동화를 정의합니다.

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [.github/CODEOWNERS](.github/CODEOWNERS) | 코드 소유권/리뷰 담당자 | 기본 소유자와 `docs/`, `.claude/`, `.github/`별 소유자를 지정해 PR 리뷰 자동 할당의 근거를 제공한다. |
| [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) | PR 제출 양식 | 변경 목적, 주요 변경 사항, 테스트/문서 갱신 여부, 리스크, Claude Code 검토를 포함하는 PR 체크리스트다. |
| [.github/ISSUE_TEMPLATE/config.yml](.github/ISSUE_TEMPLATE/config.yml) | 이슈 템플릿 설정 | `blank_issues_enabled: true`로 설정해 사전 정의 템플릿 외 자유 형식 이슈도 허용한다. |
| [.github/ISSUE_TEMPLATE/feature_request.md](.github/ISSUE_TEMPLATE/feature_request.md) | 기능 요청 템플릿 | 배경·요구사항·완료 조건·테스트 조건·문서 갱신·참고 자료 구조의 기능 요청 양식이다. |
| [.github/ISSUE_TEMPLATE/bug_report.md](.github/ISSUE_TEMPLATE/bug_report.md) | 버그 보고 템플릿 | 동일 구조로 문제 상황을 기록하는 버그 보고 양식이다. |
| [.github/ISSUE_TEMPLATE/task.md](.github/ISSUE_TEMPLATE/task.md) | 일반 작업 템플릿 | 일반 업무 작업을 위한 이슈 양식이다. |
| [.github/ISSUE_TEMPLATE/refactoring.md](.github/ISSUE_TEMPLATE/refactoring.md) | 리팩토링 템플릿 | 코드 리팩토링 작업을 위한 이슈 양식이다. |
| [.github/ISSUE_TEMPLATE/documentation.md](.github/ISSUE_TEMPLATE/documentation.md) | 문서 작업 템플릿 | 문서 작성·갱신을 위한 이슈 양식이다. |
| [.github/workflows/ci.yml](.github/workflows/ci.yml) | 기본 CI 파이프라인 | PR 및 `main`/`develop` 푸시 시 [scripts/quality/check.sh](scripts/quality/check.sh)를 실행해 품질을 검사한다. |
| [.github/workflows/docs-check.yml](.github/workflows/docs-check.yml) | 문서 검증 워크플로우 | `docs/`, `.md`, `.claude/` 변경 시 `README.md`·`CLAUDE.md`·`docs/index.md` 존재 여부를 확인해 필수 문서 베이스라인을 유지한다. |
| [.github/workflows/ai-review-checklist.yml](.github/workflows/ai-review-checklist.yml) | AI 리뷰 체크리스트 댓글 | PR 생성/동기화 시 변경 파일을 수집하고 요구사항·테스트/문서·보안/리스크 검토 항목을 PR에 자동 댓글로 남긴다. |

## 3. `.claude/` — Claude Code harness

Claude Code 에이전트의 동작 방식을 정의하는 핵심 디렉터리입니다.

### 3-1. 전역 설정

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [.claude/settings.json](.claude/settings.json) | 전역 보안·권한 설정 | 문서/소스/규칙 읽기는 허용하되 `.env`·시크릿·credentials 읽기와 파괴적 명령(`rm -rf`, `curl`, `kubectl delete` 등)을 차단하고, 편집 전후 보안 점검·포맷 훅을 자동 실행한다. |
| [.claude/settings.local.example.json](.claude/settings.local.example.json) | 로컬 환경 설정 예시 | 환경을 `local`로 두고 `npm`/`gradle`/`pytest`/`go test` 실행 권한을 추가하는 예시. 개발자가 로컬에서 테스트를 직접 돌릴 수 있게 한다. |

### 3-2. `.claude/rules/` — 코드 작성 규칙

Claude가 코드를 작성할 때 항상 적용하는 규칙 모음입니다.

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [.claude/rules/general.md](.claude/rules/general.md) | 일반 개발 원칙 | 백엔드 기준 사고, 스택 미정 인정, 변경 전 영향 범위 확인, 작업 분할, 불확실성 표시 등 5가지 기초 원칙. |
| [.claude/rules/backend-architecture.md](.claude/rules/backend-architecture.md) | 계층 설계 규칙 | API/애플리케이션/도메인/인프라 계층 분리, 트랜잭션 경계, 외부 연동 adapter 격리, 실패/재시도/타임아웃 검토를 요구한다. |
| [.claude/rules/code-style.md](.claude/rules/code-style.md) | 코드 스타일 | 읽기 쉬운 네이밍, 단일 책임 함수, 매직 넘버 상수화, 의미 있는 예외 등을 정의한다. |
| [.claude/rules/git-workflow.md](.claude/rules/git-workflow.md) | Git 규칙 | 브랜치 `` `<type>/<short-description>` ``, 커밋 Conventional Commits(예: `feat: add login api`)을 권장한다. |
| [.claude/rules/api-design.md](.claude/rules/api-design.md) | API 설계 규칙 | 리소스 중심 URL, request/response 스키마 문서화, 일관된 에러 응답, breaking change 시 versioning/migration 검토를 요구한다. |
| [.claude/rules/database.md](.claude/rules/database.md) | DB 변경 안전성 | 마이그레이션 되돌림 가능성, NOT NULL 추가 시 호환성, 인덱스 lock/성능 영향, 트랜잭션 경계 문서화를 다룬다. |
| [.claude/rules/testing.md](.claude/rules/testing.md) | 테스트 규칙 | 기능 변경 시 테스트 필수, 실패/경계값 포함, 외부 시스템 mock/fake/testcontainer 격리를 규정한다. |
| [.claude/rules/documentation.md](.claude/rules/documentation.md) | 문서 작성 기준 | 문서는 목적·결정 배경 설명, ADR은 선택/미선택 대안 기록, 운영 문서는 장애 시 따라할 수 있게 작성하도록 한다. |
| [.claude/rules/security.md](.claude/rules/security.md) | 보안 규칙 | 시크릿 읽기/출력 금지, 운영 데이터 변경 자동 실행 금지, 인증/인가/입력 검증/권한 경계 검토를 강제한다. |
| [.claude/rules/tech-stack.md](.claude/rules/tech-stack.md) | 기술 스택 템플릿 | 스택이 미정임을 명시하고, 확정 후 언어·프레임워크·DB·캐시·브로커·테스트·배포·관찰성을 채우도록 구조화돼 있다. |

### 3-3. `.claude/skills/` — 반복 작업 절차(Skill)

자주 수행하는 작업 절차를 표준화한 스킬 정의입니다.

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [.claude/skills/create-feature-plan/SKILL.md](.claude/skills/create-feature-plan/SKILL.md) | 기능 기획 | 요구사항을 분석해 구현 계획·영향 범위·테스트 계획·문서 갱신 계획을 표준 형식으로 수립한다. |
| [.claude/skills/create-backend-api/SKILL.md](.claude/skills/create-backend-api/SKILL.md) | 백엔드 API 설계 | endpoint와 request/response·service·persistence·test 초안을 생성한다(HTTP method, 인증, 실패 케이스, 트랜잭션 검토). |
| [.claude/skills/write-tests/SKILL.md](.claude/skills/write-tests/SKILL.md) | 테스트 작성 | 정상·실패·경계값·권한·외부 실패·동시성/재시도 시나리오를 포괄하는 테스트 초안을 만든다. |
| [.claude/skills/review-pr/SKILL.md](.claude/skills/review-pr/SKILL.md) | PR 리뷰 | 품질·테스트·문서·보안·운영 리스크 관점으로 PR을 종합 리뷰한다. |
| [.claude/skills/update-docs/SKILL.md](.claude/skills/update-docs/SKILL.md) | 문서 갱신 | 코드/정책 변경에 맞춰 README, project, convention, architecture 등 관련 문서를 갱신한다. |
| [.claude/skills/update-adr/SKILL.md](.claude/skills/update-adr/SKILL.md) | ADR 기록 | 기술/정책 결정을 Context·Decision·Alternatives·Consequences·Validation 형식으로 기록한다. |
| [.claude/skills/deployment-check/SKILL.md](.claude/skills/deployment-check/SKILL.md) | 배포 전 점검 | migration·API 호환성·환경변수·테스트·롤백 가능성을 점검하고 배포 가능/조건부/보류를 판단한다. |

### 3-4. `.claude/agents/` — 전문 서브에이전트

특정 역할에 특화된 서브에이전트 정의입니다(각각 모델·접근 도구·표시 색이 지정됨).

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [.claude/agents/planner.md](.claude/agents/planner.md) | 기획 에이전트 | 요구사항을 분석하고 작업 계획·리스크를 정리한다. |
| [.claude/agents/backend-architect.md](.claude/agents/backend-architect.md) | 아키텍처 에이전트 | 아키텍처, 모듈 경계, 트랜잭션, 외부 연동 구조를 검토한다. |
| [.claude/agents/code-reviewer.md](.claude/agents/code-reviewer.md) | 코드 리뷰 에이전트 | 변경 코드를 품질·유지보수성·테스트·문서 관점에서 리뷰한다. |
| [.claude/agents/test-writer.md](.claude/agents/test-writer.md) | 테스트 에이전트 | 테스트 전략·시나리오를 설계하고 테스트 초안을 작성한다. |
| [.claude/agents/docs-maintainer.md](.claude/agents/docs-maintainer.md) | 문서 유지보수 에이전트 | 코드·정책 변경에 맞춰 문서를 갱신해 일관성을 유지한다. |
| [.claude/agents/security-reviewer.md](.claude/agents/security-reviewer.md) | 보안 검토 에이전트 | 보안, Secret, 권한, 위험 명령을 검토한다. |

### 3-5. `.claude/harness/` — 멀티 에이전트 harness 프롬프트/템플릿

장기·멀티 에이전트 작업의 동작 규칙과 상태 추적을 표준화합니다.

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [.claude/harness/initializer-prompt.md](.claude/harness/initializer-prompt.md) | 초기화 프롬프트 | 세션 시작 시 README·CLAUDE.md·docs/project를 검토하고 task-board/progress를 초기화한다. |
| [.claude/harness/coding-agent-prompt.md](.claude/harness/coding-agent-prompt.md) | 개발 에이전트 프롬프트 | task-board에서 작업을 골라 단계적으로 진행하고 각 단계 progress를 기록하도록 지시한다. |
| [.claude/harness/evaluator-agent-prompt.md](.claude/harness/evaluator-agent-prompt.md) | 검증 에이전트 프롬프트 | 완료 결과물을 요구사항·테스트·문서·보안·운영 리스크 5기준으로 검증한다. |
| [.claude/harness/progress-template.md](.claude/harness/progress-template.md) | 진행 상황 템플릿 | Session·Completed·In Progress·Blocked·Decisions·Next Steps 6섹션 진행 기록 양식. |
| [.claude/harness/task-board-template.md](.claude/harness/task-board-template.md) | 작업 보드 템플릿 | Todo·In Progress·Done·Blocked 4열 칸반 보드 양식. |
| [.claude/harness/handoff-template.md](.claude/harness/handoff-template.md) | 인수인계 템플릿 | Context·What changed·What remains·Risks·Suggested next prompt 5항목 인계 양식. |

## 4. `docs/` — 사람이 읽는 문서

> 아래 문서들은 **재사용 템플릿 + 구체 예시(Spring Boot 기준)**로 채워져 있습니다. 가상 도메인 예시이므로, 프로젝트 확정 후 실제 값으로 교체해 사용합니다.

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [docs/index.md](docs/index.md) | 문서 네비게이션 엔트리 | 전체 문서의 목적·내용을 안내하는 인덱스. 11개 하위 폴더를 표로 정리한 목차(작성됨). |

### 4-1. `docs/project/` · `docs/requirements/` · `docs/adr/`

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [docs/project/project-brief.md](docs/project/project-brief.md) | 프로젝트 개요 | 프로젝트의 목적·범위·주요 요구사항(가상 'Meetup' 도메인 예시). 확정 후 실제 프로젝트 내용으로 교체한다. |
| [docs/requirements/non-functional-requirements.md](docs/requirements/non-functional-requirements.md) | 비기능 요구사항 | 성능·보안·확장성·가용성 등 품질 요구사항(예시 포함, Spring Boot 기준). |
| [docs/requirements/user-story-template.md](docs/requirements/user-story-template.md) | 사용자 스토리 템플릿 | 사용자 스토리를 일관 형식으로 작성하기 위한 템플릿. |
| [docs/requirements/acceptance-criteria-template.md](docs/requirements/acceptance-criteria-template.md) | 합격 기준 템플릿 | 스토리 완료 조건을 명확히 정의하기 위한 템플릿. |
| [docs/adr/README.md](docs/adr/README.md) | ADR 저장소 안내 | 중요 기술 결정을 기록·추적하는 ADR 폴더의 목적·사용법 안내(예시 포함, Spring Boot 기준). |
| [docs/adr/0000-adr-template.md](docs/adr/0000-adr-template.md) | ADR 표준 템플릿 | 모든 ADR이 따를 구조(Status·Context·Decision·Alternatives·Consequences·Validation)를 정의한다. |

### 4-2. `docs/convention/` · `docs/api/`

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [docs/convention/branch-convention.md](docs/convention/branch-convention.md) | 브랜치 컨벤션 | 브랜치 네이밍·관리 규칙(예시 포함, Spring Boot 기준). |
| [docs/convention/code-style.md](docs/convention/code-style.md) | 코드 스타일 | 네이밍·포맷팅 등 코드 작성 스타일(예시 포함, Spring Boot 기준). |
| [docs/convention/commit-convention.md](docs/convention/commit-convention.md) | 커밋 컨벤션 | 커밋 메시지 작성 규칙(예시 포함, Spring Boot 기준). |
| [docs/convention/documentation-convention.md](docs/convention/documentation-convention.md) | 문서화 컨벤션 | 코드 문서화·README·API 문서 생성 방식(예시 포함, Spring Boot 기준). |
| [docs/convention/pr-convention.md](docs/convention/pr-convention.md) | PR 컨벤션 | PR 제목·설명·리뷰 규칙(예시 포함, Spring Boot 기준). |
| [docs/api/api-design-guide.md](docs/api/api-design-guide.md) | API 설계 가이드 | RESTful endpoint·요청/응답 구조 등 설계 원칙(예시 포함, Spring Boot 기준). |
| [docs/api/error-response-guide.md](docs/api/error-response-guide.md) | 에러 응답 가이드 | 에러 응답 형식·코드·메시지 정의 방식(예시 포함, Spring Boot 기준). |
| [docs/api/versioning-policy.md](docs/api/versioning-policy.md) | 버전 관리 정책 | API 버전 전략·하위호환성·마이그레이션 방식(예시 포함, Spring Boot 기준). |

### 4-3. `docs/architecture/`

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [docs/architecture/system-overview.md](docs/architecture/system-overview.md) | 시스템 개요 | 시스템 전체의 고수준 구조 개요(컨텍스트 다이어그램 예시 포함). 확정 후 실제 시스템에 맞게 구체화한다. |
| [docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md) | 백엔드 아키텍처 | 전체 아키텍처 패턴·계층 구조·기술 스택(예시 포함, Spring Boot 기준). |
| [docs/architecture/module-boundary.md](docs/architecture/module-boundary.md) | 모듈 경계 | 모듈 책임 범위·의존성·통신 인터페이스(예시 포함, Spring Boot 기준). |
| [docs/architecture/external-integration.md](docs/architecture/external-integration.md) | 외부 연동 | 외부 API 연동·인증·레이트 리미팅·동기화 패턴(예시 포함, Spring Boot 기준). |
| [docs/architecture/observability.md](docs/architecture/observability.md) | 관찰성 | 로깅·메트릭·추적·알림 기준(예시 포함, Spring Boot 기준). |

### 4-4. `docs/database/` · `docs/testing/`

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [docs/database/database-design.md](docs/database/database-design.md) | DB 설계 | 데이터베이스 구조·스키마·관계 설계(예시 포함, Spring Boot 기준). |
| [docs/database/migration-policy.md](docs/database/migration-policy.md) | 마이그레이션 정책 | 스키마 변경·버전 관리·롤백 절차(예시 포함, Spring Boot 기준). |
| [docs/database/transaction-policy.md](docs/database/transaction-policy.md) | 트랜잭션 정책 | 데이터 일관성·ACID·동시성 제어 규칙(예시 포함, Spring Boot 기준). |
| [docs/testing/testing-strategy.md](docs/testing/testing-strategy.md) | 테스트 전략 | 단위/통합/E2E 피라미드·커버리지 기준(예시 포함, Spring Boot 기준). |
| [docs/testing/unit-test-guide.md](docs/testing/unit-test-guide.md) | 단위 테스트 가이드 | 함수/메서드 단위 테스트 작성법·Mock 사용(예시 포함, Spring Boot 기준). |
| [docs/testing/integration-test-guide.md](docs/testing/integration-test-guide.md) | 통합 테스트 가이드 | 모듈/계층 간 상호작용 테스트·DB 통합 테스트(예시 포함, Spring Boot 기준). |
| [docs/testing/e2e-test-guide.md](docs/testing/e2e-test-guide.md) | E2E 테스트 가이드 | 사용자 시나리오 기반 엔드투엔드 테스트(예시 포함, Spring Boot 기준). |
| [docs/testing/test-data-guide.md](docs/testing/test-data-guide.md) | 테스트 데이터 가이드 | Fixture 작성·데이터 생성/격리/정리 전략(예시 포함, Spring Boot 기준). |

### 4-5. `docs/operations/` · `docs/security/`

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [docs/operations/deployment-guide.md](docs/operations/deployment-guide.md) | 배포 가이드 | 배포 과정을 단계별로 설명(예시 포함, Spring Boot 기준). |
| [docs/operations/rollback-guide.md](docs/operations/rollback-guide.md) | 롤백 가이드 | 문제 발생 시 이전 버전 복귀 전략·절차(예시 포함, Spring Boot 기준). |
| [docs/operations/runbook.md](docs/operations/runbook.md) | 운영 런북 | 반복적인 운영 작업·절차 기록(예시 포함, Spring Boot 기준). |
| [docs/operations/incident-response.md](docs/operations/incident-response.md) | 장애 대응 | 장애 발생 시 대응 절차·에스컬레이션(예시 포함, Spring Boot 기준). |
| [docs/security/security-policy.md](docs/security/security-policy.md) | 보안 정책 | 데이터 보호·접근 제어·감사 로그 등 보안 원칙(예시 포함, Spring Boot 기준). |
| [docs/security/access-control.md](docs/security/access-control.md) | 접근 제어 | 권한 관리·RBAC·리소스 권한 규칙(예시 포함, Spring Boot 기준). |

## 5. `harness/` — 장기 작업 진행 상태

실제 진행 중인 작업의 상태·인수인계를 기록하는 운영용 파일입니다(`.claude/harness/`가 "템플릿/프롬프트"라면, 여기는 "실제 기록").

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [harness/task-board.md](harness/task-board.md) | 작업 보드 | Todo/In Progress/Done/Blocked 칸반. 사용법과 예시 카드가 채워져 있다. |
| [harness/claude-progress.md](harness/claude-progress.md) | 진행 기록 | 세션별 목표·완료·의사결정 기록. 예시 세션과 새 세션 복사용 템플릿이 포함돼 있다. |
| [harness/evaluator-report.md](harness/evaluator-report.md) | 평가 리포트 | 요구사항/테스트/문서/보안/운영 5기준 평가 리포트. 예시 리포트 2건과 신규 템플릿이 포함돼 있다. |

## 6. `scripts/` — 품질 검사와 Claude hooks

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [scripts/setup.sh](scripts/setup.sh) | 환경 초기화 | 최초 설정 시 `CLAUDE.local.example.md`를 `CLAUDE.local.md`로 복사해 로컬 설정을 준비한다. |
| [scripts/quality/check.sh](scripts/quality/check.sh) | 품질 검증 | README·CLAUDE.md·문서 인덱스·설정 파일 존재 여부를 확인하고 마크다운 목록을 생성하는 기본 품질 검사. CI에서 호출된다. |
| [scripts/claude-hooks/protect-sensitive-path.sh](scripts/claude-hooks/protect-sensitive-path.sh) | 민감 경로 차단 훅 | `.env`·secrets·credentials 등에 대한 Claude의 Read/Edit 접근을 사전에 차단한다. |
| [scripts/claude-hooks/format-after-edit.sh](scripts/claude-hooks/format-after-edit.sh) | 편집 후처리 훅 | 파일 편집 후 실행돼 편집 경로를 로깅한다. 향후 자동 포맷/린트 확장의 기반. |
| [scripts/claude-hooks/pre-pr-check.sh](scripts/claude-hooks/pre-pr-check.sh) | PR 전 검증 훅 | PR 생성 전 품질 검사와 git 상태 확인을 수행해 기본 품질 기준 충족을 보장한다. |

## 7. `src/` — 실제 백엔드 코드 위치

| 파일 | 역할 | 설명 |
| --- | --- | --- |
| [src/main/.gitkeep](src/main/.gitkeep) | 메인 소스 자리표시 | 빈 파일로 `src/main/` 디렉터리를 git에 유지한다. 실제 백엔드 코드가 들어갈 자리. |
| [src/test/.gitkeep](src/test/.gitkeep) | 테스트 자리표시 | 빈 파일로 `src/test/` 디렉터리를 git에 유지한다. 실제 테스트 코드가 들어갈 자리. |

---

## 검증 명령

```bash
bash scripts/quality/check.sh
bash scripts/claude-hooks/pre-pr-check.sh
```

