# CLAUDE.md

## Project Context

이 저장소는 특정 백엔드 서비스가 정해지기 전 사용할 수 있는 base repository입니다.

Claude Code는 다음 전제를 따른다.

- 백엔드 프로젝트용 base repository이다.
- 아직 도메인, 언어, 프레임워크, DB, 배포 방식은 미정이다.
- Spring Boot, NestJS, FastAPI, Go, Django 등으로 확장될 수 있다.
- 특정 기술 스택을 사용자가 확정하기 전까지 임의로 프로젝트를 초기화하지 않는다.
- 공통 컨벤션, 문서, 테스트/운영/보안 기준, Claude Code harness를 먼저 관리한다.

---

## How To Work

작업을 시작하면 다음 순서를 따른다.

1. 요청을 요약한다.
2. 프로젝트 미정 상태에서 가능한 작업과 확정이 필요한 작업을 구분한다.
3. 변경할 파일과 이유를 먼저 제시한다.
4. 작은 단위로 파일을 수정한다.
5. 검증 명령 또는 수동 확인 방법을 남긴다.
6. 장기 작업이면 `harness/claude-progress.md`에 진행 상황을 남긴다.

---

## Repository Rules

- Claude가 항상 알아야 하는 핵심 지침은 `CLAUDE.md`에 둔다.
- Claude가 코드 작성 중 적용해야 하는 규칙은 `.claude/rules/`에 둔다.
- 반복 작업 절차는 `.claude/skills/`에 둔다.
- 전문 역할은 `.claude/agents/`에 둔다.
- 사람이 읽는 상세 문서는 `docs/`에 둔다.
- 장기 작업 진행 상태는 `harness/`에 둔다.
- 기술 결정은 `docs/adr/`에 ADR로 남긴다.

---

## Backend Principles

백엔드 프로젝트가 정해지면 다음 기준을 우선한다.

- API, 비즈니스 로직, 데이터 접근, 외부 연동 책임을 분리한다.
- 트랜잭션 경계를 명확히 한다.
- 실패, 재시도, 중복 요청, 외부 시스템 장애를 고려한다.
- 테스트 가능한 구조를 만든다.
- 로그, 메트릭, 추적, 알림 기준을 문서화한다.
- Secret과 운영 설정은 코드에 하드코딩하지 않는다.

---

## Commands

현재는 기술 스택 미정이므로 공통 검증만 수행한다.

```bash
bash scripts/quality/check.sh
bash scripts/claude-hooks/pre-pr-check.sh
```

프로젝트가 정해지면 실제 명령을 추가한다.

```bash
# examples
./gradlew test
npm test
pytest
go test ./...
```

---

## Safety Rules

Claude Code는 다음 작업을 자동 수행하지 않는다.

- Secret, API Key, Token 읽기 또는 출력
- `.env`, credentials, private key 파일 읽기
- 운영 DB 수정
- 운영 배포
- 비용이 발생할 수 있는 클라우드 리소스 생성/삭제
- `rm -rf` 같은 파괴적 명령 실행
- 사용자가 확정하지 않은 기술 스택으로 프로젝트 강제 초기화
