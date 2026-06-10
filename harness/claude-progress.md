# Claude Progress

> 장기 작업의 진행 맥락을 세션 단위로 누적 기록한다.
> 대응 템플릿: [.claude/harness/progress-template.md](../.claude/harness/progress-template.md)
> 관련 보드: [task-board.md](./task-board.md) · 검증 결과: [evaluator-report.md](./evaluator-report.md)
>
> **예시 안내**: 아래 세션 기록은 "사용법을 보여주는 예시"다. 날짜는 `<YYYY-MM-DD>` placeholder 또는 "예시"로 표기한다.
> 실제 작업에서는 placeholder를 실제 값으로 교체한다. 임의의 가짜 오늘 날짜를 만들어 적지 않는다.
> 예시 스택은 `Spring Boot 3.x / Java 17 / Gradle / JPA / PostgreSQL / Flyway / JUnit5 + Testcontainers / Spring Security(JWT)`다.

---

## 기록 방법

- 새 세션은 **맨 위**에 추가한다(최신이 위).
- 각 세션은 `Date / Goal / Status` 헤더 + `Completed / In Progress / Blocked / Decisions / Next Steps` 섹션으로 구성한다.
- `Status`는 `진행중 | 완료 | 차단` 중 하나로 적는다.
- 중요한 기술 결정은 여기 `Decisions`에 요약하고, 정식 결정은 [docs/adr/](../docs/adr/)에 ADR로 남긴다.

---

## Session 2 — 문서 예시 템플릿 작성

- **Date**: `<YYYY-MM-DD>` (예시)
- **Goal**: base repo의 docs/ 및 harness/ 스텁을 "예시가 포함된 실전 템플릿"으로 확장
- **Status**: 진행중

### Completed

- harness 기록 파일 3종을 예시 포함 템플릿으로 작성: [task-board.md](./task-board.md), [claude-progress.md](./claude-progress.md), [evaluator-report.md](./evaluator-report.md)
- 각 문서 상단에 "예시(Spring Boot 기준)" 고지 추가, 실제 값 교체 안내 포함

### In Progress

- 나머지 docs 그룹(architecture/api/database/testing/operations/security)의 예시 채움
- 문서 간 상대경로 링크 정합성 점검

### Blocked

- 없음

### Decisions

- 예시 스택을 `Spring Boot 3.x / Java 17` 계열로 통일해 문서 간 일관성 확보. **단, 이는 예시이며 스택 확정 시 교체**한다.
- 날짜는 실제 값을 임의 생성하지 않고 `<YYYY-MM-DD>` placeholder를 사용한다.
- 비밀값·실주소·실명은 전부 가짜 예시(`example.com`, `<YOUR_VALUE>`)로 적는다. 근거: [docs/security/secrets-management.md](../docs/security/secrets-management.md)

### Next Steps

- [ ] architecture 그룹 예시 채움 ([system-overview](../docs/architecture/system-overview.md) 등)
- [ ] api 그룹 예시 채움 ([api-design-guide](../docs/api/api-design-guide.md) 등)
- [ ] 전체 문서 링크 깨짐 검사 후 [evaluator-report.md](./evaluator-report.md) 갱신

---

## Session 1 — base repository 초기화

- **Date**: `<YYYY-MM-DD>` (예시)
- **Goal**: 기술 스택 미정 상태에서 공통 골격(문서/규칙/harness/스크립트) 초기화
- **Status**: 완료

### Completed

- 디렉터리 골격 생성: `docs/`, `.claude/rules/`, `.claude/skills/`, `.claude/agents/`, `.claude/harness/`, `harness/`, `scripts/`
- [CLAUDE.md](../CLAUDE.md) 작성 — 프로젝트 전제, 작업 절차, 안전 규칙 정의
- [.claude/rules/](../.claude/rules/) 9종 규칙 스텁 배치 (api-design, backend-architecture, security 등)
- 품질 스크립트 스텁: [scripts/quality/check.sh](../scripts/quality/check.sh), [scripts/claude-hooks/pre-pr-check.sh](../scripts/claude-hooks/pre-pr-check.sh)

### In Progress

- 없음 (다음 세션으로 인계)

### Blocked

- 실제 빌드/테스트 명령 확정 — 스택 미정으로 보류 ([task-board.md](./task-board.md) Blocked 참고)

### Decisions

- Claude가 항상 따르는 핵심 지침은 [CLAUDE.md](../CLAUDE.md), 코드 작성 규칙은 [.claude/rules/](../.claude/rules/), 사람용 상세 문서는 [docs/](../docs/)로 **역할을 분리**한다.
- 스택 확정 전에는 프로젝트를 임의 초기화하지 않는다(예: `build.gradle` 생성 금지).

### Next Steps

- [ ] 사용자에게 기술 스택 확정 요청 → [.claude/rules/tech-stack.md](../.claude/rules/tech-stack.md) 채움
- [ ] [docs/project/project-brief.md](../docs/project/project-brief.md) 작성
- [ ] 첫 도메인 API 후보 정의 (예: 회원가입/로그인)

---

## (예시) 새 세션 추가 템플릿 — 복사해서 위에 붙여넣기

```markdown
## Session N — <한 줄 제목>

- **Date**: <YYYY-MM-DD>
- **Goal**: <이번 세션의 목표 한 줄>
- **Status**: 진행중 | 완료 | 차단

### Completed
- <끝낸 일>

### In Progress
- <진행 중인 일과 현재 위치>

### Blocked
- <차단 항목 + 사유 + 해제 조건> (없으면 "없음")

### Decisions
- <내린 결정과 근거> (정식 결정은 docs/adr/ 에 ADR 추가)

### Next Steps
- [ ] <다음에 할 일>
```
