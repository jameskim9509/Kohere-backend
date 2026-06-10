# Task Board

> 이 보드는 진행 중인 작업을 칸반 형식으로 추적한다.
> 대응 템플릿: [.claude/harness/task-board-template.md](../.claude/harness/task-board-template.md)
>
> **예시 안내**: 아래 카드 중 일부는 "사용법을 보여주기 위한 예시(Spring Boot 기준)"이다.
> 실제 프로젝트에서는 예시 카드를 지우고 자신의 작업으로 교체한다.
> 기술 스택은 사용자가 확정하기 전까지 미정이며, 예시는 `Spring Boot 3.x / Java 17 / Gradle / JPA / PostgreSQL / Flyway / JUnit5 + Testcontainers / Spring Security(JWT)`를 가정한다.

---

## 사용법

- 카드는 `- [ ] [우선순위] 설명 (담당/메모)` 형식으로 적는다.
- 카드 하나는 "반나절~하루 안에 끝낼 수 있는 단위"로 쪼갠다.
- 칸을 옮길 때 `(YYYY-MM-DD)` 형태로 이동 날짜를 남긴다.
- Blocked로 보낼 때는 반드시 **차단 사유**와 **해제 조건**을 적는다.
- 상세 진행 맥락은 [claude-progress.md](./claude-progress.md)에, 검증 결과는 [evaluator-report.md](./evaluator-report.md)에 남긴다.

우선순위 표기

| 표기 | 의미 | 예시 |
| --- | --- | --- |
| `[P0]` | 즉시 처리 / 차단 해소 | 빌드 깨짐, 보안 사고 |
| `[P1]` | 이번 사이클 핵심 | 첫 도메인 API 구현 |
| `[P2]` | 있으면 좋음 | 문서 보강, 리팩터링 |

---

## Todo

- [ ] `[P1]` 기술 스택 확정 후 [.claude/rules/tech-stack.md](../.claude/rules/tech-stack.md) 채우기 (언어/프레임워크/DB/캐시/브로커/테스트/배포/관측)
- [ ] `[P1]` 확정 스택에 맞춰 [docs/project/project-brief.md](../docs/project/project-brief.md) 작성 (도메인, 목표, 범위)
- [ ] `[P1]` 첫 도메인 API 구현 — 예: `POST /api/v1/users` 회원가입 (요청/응답 스키마 + 검증 + 영속화 + 테스트)
- [ ] `[P1]` 인증 토대 잡기 — 예: Spring Security + JWT 로그인 `POST /api/v1/auth/login`
- [ ] `[P2]` 실제 테스트 명령을 [scripts/quality/check.sh](../scripts/quality/check.sh)와 [CLAUDE.md](../CLAUDE.md)에 반영 (예: `./gradlew test`)
- [ ] `[P2]` CI 파이프라인 초안 (lint → build → test → 커버리지 게이트)
- [ ] `[P2]` Flyway 초기 마이그레이션 `V1__init.sql` 작성 및 [docs/database/migration-policy.md](../docs/database/migration-policy.md)와 정합성 확인

## In Progress

- [ ] `[P1]` 문서 예시 템플릿 작성 — 그룹별로 docs/ 와 harness/ 채우는 중 (담당: tech-writer, 시작 2026-06-09)
  - 진행 맥락: [claude-progress.md](./claude-progress.md) Session 2 참고

## Done

- [x] `[P2]` Backend base repository 템플릿 초기화 — 디렉터리 골격, CLAUDE.md, .claude/rules, docs 스텁 생성 (2026-06-09)
- [x] `[P2]` Claude Code harness 골격 배치 — `.claude/harness/*-template.md`, `harness/` 기록 파일 생성 (2026-06-09)
- [x] `[P2]` 품질 스크립트 스텁 배치 — [scripts/quality/check.sh](../scripts/quality/check.sh), [scripts/claude-hooks/pre-pr-check.sh](../scripts/claude-hooks/pre-pr-check.sh) (2026-06-09)

## Blocked

- [ ] `[P1]` 실제 테스트/빌드 명령 확정
  - **차단 사유**: 언어·프레임워크 미정이라 `./gradlew test` / `npm test` / `pytest` 중 무엇을 쓸지 결정 불가
  - **해제 조건**: 사용자가 기술 스택 확정 → [.claude/rules/tech-stack.md](../.claude/rules/tech-stack.md) 갱신
  - **차단 시작**: 2026-06-09
- [ ] `[P1]` 배포 파이프라인 설계
  - **차단 사유**: 배포 타깃(컨테이너/서버리스/온프레미스) 미정
  - **해제 조건**: [docs/operations/deployment-guide.md](../docs/operations/deployment-guide.md)에 배포 방식 결정 기록

---

## (예시) 카드가 흐르는 모습

아래는 한 카드가 칸반을 통과하는 과정을 보여주는 참고 예시다(Spring Boot 기준). 실제 보드에는 옮겨 적지 않는다.

```text
Todo                      In Progress                 Done
─────────────────────     ─────────────────────       ─────────────────────
[P1] 회원가입 API   ─►     [P1] 회원가입 API     ─►     [P1] 회원가입 API
  · 요청 스키마             · DTO + Validation 완료      · 2026-06-12 머지
  · 영속화                  · Repository 작성 중          · 커버리지 84%
  · 테스트                                                · evaluator: PASS
```

차단되는 예시

```text
In Progress  ─►  Blocked
[P1] 결제 연동      [P1] 결제 연동
                   사유: PG사 샌드박스 키 미발급
                   해제: 인프라팀 키 전달 (티켓 OPS-123)
```
