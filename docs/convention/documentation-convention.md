# Documentation Convention

> **예시(Spring Boot 기준) 안내**
> JavaDoc/주석 예시는 **Java 17 / Spring Boot 3.x** 기준의 **예시**입니다. 다른 언어로 확정되면 해당 언어의 doc 표준(예: TSDoc, Python docstring, GoDoc)으로 교체하세요.
> 마크다운/문서 위치/ADR 규칙은 스택과 무관하게 동일합니다.
> 관련 규칙: [.claude/rules/documentation.md](../../.claude/rules/documentation.md)

## 목적

문서가 **어디에**, **어떤 형식으로** 존재해야 하는지 통일한다. 문서는 코드의 *목적과 결정 배경*을 설명하고, 운영 문서는 *실제 장애 상황에서 따라 할 수 있어야* 한다.

---

## 1. 문서 위치 규칙

CLAUDE.md의 Repository Rules를 따른다.

| 종류 | 위치 | 독자 | 예시 |
| --- | --- | --- | --- |
| Claude 핵심 지침 | `CLAUDE.md` | Claude Code | 프로젝트 전제, 안전 규칙 |
| 코드 작성 규칙 | `.claude/rules/` | Claude Code | [api-design.md](../../.claude/rules/api-design.md) |
| 반복 작업 절차 | `.claude/skills/` | Claude Code | 스킬 정의 |
| 전문 역할 | `.claude/agents/` | Claude Code | 에이전트 정의 |
| 사람이 읽는 상세 문서 | `docs/` | 사람(팀) | 본 문서, [convention](../index.md) |
| 기술 결정 기록 | `docs/adr/` | 팀 | ADR 0001, 0002 ... |
| 장기 작업 진행 상태 | `harness/` | 팀/Claude | `harness/claude-progress.md` |

```text
docs/
├── index.md           # 문서 인덱스(목차)
├── architecture/      # 시스템 구조, 다이어그램
├── api/               # API 명세, migration 가이드
├── convention/        # 팀 컨벤션(본 폴더)
├── database/          # 스키마, 마이그레이션 정책
├── operations/        # 운영/장애 대응 런북
├── security/          # 보안 기준
├── testing/           # 테스트 전략
└── adr/               # Architecture Decision Records
```

> 문서를 추가하면 [docs/index.md](../index.md)에 링크를 등록한다.

---

## 2. 마크다운 스타일 규칙

| 항목 | 규칙 | 예시 |
| --- | --- | --- |
| 제목 | 파일당 H1(`#`)은 1개, 이후 H2(`##`)부터 계층 사용 | `# API Design Guide` |
| 파일명 | 소문자 + 하이픈(kebab-case) | `api-design-guide.md` |
| 링크 | 문서 간은 **상대경로** 마크다운 링크 | `[system-overview](../architecture/system-overview.md)` |
| 코드블록 | 언어 지정(```` ```java ````, ```` ```bash ````, ```` ```json ````) | 하이라이트/가독성 |
| 표 | 의사결정/대조에 적극 사용 | 좋은예/나쁜예 표 |
| 줄바꿈 | 문단 사이 빈 줄 1개 | - |
| 예시 값 | 가짜 값만 사용 | `user@example.com`, `<YOUR_VALUE>` |
| 가정 표기 | 불확실한 내용은 명시 | `> **가정:** ...` |

- 깨진 링크가 없도록 상대경로를 검증한다.
- 너무 긴 문서는 섹션을 분리하고 인덱스에서 연결한다.
- 다이어그램은 ASCII 또는 Mermaid를 사용한다.

```text
[Client] --HTTP--> [API Layer] --> [Application] --> [Domain]
                                         |
                                         v
                                   [Infra/DB(PostgreSQL)]
```

---

## 3. JavaDoc / 주석 규칙 (예시: Java)

- 공개 API(컨트롤러 공개 메서드, 공용 라이브러리), 복잡한 비즈니스 로직에는 JavaDoc을 작성한다.
- 주석은 "무엇을"이 아니라 **"왜"** 를 설명한다. 코드로 자명한 내용은 주석 금지.
- `// TODO`/`// FIXME`에는 맥락과(가능하면) 이슈 번호를 남긴다.

```java
/**
 * 주문을 취소한다.
 *
 * <p>이미 결제 완료된 주문은 환불 프로세스를 거쳐야 하므로 여기서 바로 취소할 수 없다.
 * 동시 요청에 의한 중복 취소를 막기 위해 낙관적 락을 사용한다.
 *
 * @param orderId 취소할 주문 식별자
 * @throws OrderNotFoundException  주문이 존재하지 않는 경우
 * @throws IllegalOrderStateException 이미 결제 완료/취소된 경우
 */
@Transactional
public void cancel(Long orderId) { ... }
```

```java
// 좋은 주석: 왜 그렇게 했는지
// 외부 PG 응답 지연(최대 3s)을 고려해 타임아웃을 보수적으로 설정한다.
private static final Duration PG_TIMEOUT = Duration.ofSeconds(5);

// 나쁜 주석: 코드로 자명함
i++; // i를 1 증가시킨다
```

---

## 4. ADR 작성 시점

다음과 같은 **되돌리기 어렵거나 광범위한 영향**을 주는 결정은 ADR로 남긴다([docs/adr](../adr/README.md), 스킬: [update-adr]).

| ADR 작성 대상(예시) | ADR 불필요(예시) |
| --- | --- |
| DB 선택(PostgreSQL vs MySQL) | 변수명 변경 |
| 인증 방식(JWT vs 세션) | 사소한 리팩터링 |
| 브랜치 전략(트렁크 vs git-flow) | 오타 수정 |
| 외부 메시지 브로커 도입 | 단일 함수 추가 |
| API 버저닝 정책 변경 | 문서 문구 수정 |

ADR은 **선택한 대안과 버린 대안을 함께** 기록한다([.claude/rules/documentation.md](../../.claude/rules/documentation.md)).

### ADR 템플릿(예시)

```markdown
# ADR-0001: 데이터베이스로 PostgreSQL 선택

## Status
Accepted (2026-06-09)

## Context
관계형 데이터 정합성과 트랜잭션이 중요하고, 팀의 운영 경험이 있다.

## Decision
PostgreSQL 15를 기본 RDBMS로 사용한다.

## Alternatives
- MySQL 8: 익숙하나 일부 SQL/JSON 기능 제약 → 보류
- MongoDB: 스키마 유연하나 트랜잭션 요구에 부적합 → 기각

## Consequences
- Flyway 기반 마이그레이션 도입 필요
- 로컬/CI는 Testcontainers로 동일 버전 사용
```

> ADR 파일명: `docs/adr/NNNN-제목.md` (예: `0001-use-postgresql.md`), 번호는 순차 증가.

---

## 5. 문서 최신성 유지

- 코드/정책 변경 시 관련 문서를 **같은 PR**에서 갱신한다([pr-convention](./pr-convention.md)의 "문서 갱신" 체크).
- 변경했는데 문서가 불필요하면 PR에 사유를 남긴다.
- 오래된 문서는 삭제하거나 "Deprecated" 표기 후 대체 링크를 제공한다.

---

## 체크리스트

- [ ] 문서가 올바른 위치(`docs/`, `.claude/`, `harness/`)에 있는가
- [ ] 파일명이 kebab-case이고 H1이 1개인가
- [ ] 문서 간 링크가 상대경로로 연결되고 깨지지 않는가
- [ ] 예시에 실제 secret/주소/실명 없이 가짜 값만 썼는가
- [ ] 공개 API/복잡 로직에 JavaDoc(또는 해당 언어 doc)이 있는가
- [ ] 광범위한 기술 결정은 ADR로 기록했는가
- [ ] 코드/정책 변경과 같은 PR에서 문서를 갱신했는가
