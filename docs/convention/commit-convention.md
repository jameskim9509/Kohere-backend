# Commit Convention

> **예시(Spring Boot 기준) 안내**
> 커밋 규칙은 스택과 무관하게 동일합니다. 아래 커밋 메시지 예시의 도메인/스코프만 프로젝트에 맞게 교체하세요.
> 관련 규칙: [.claude/rules/git-workflow.md](../../.claude/rules/git-workflow.md)
> 연계 문서: [branch-convention](./branch-convention.md), [pr-convention](./pr-convention.md)

## 목적

[Conventional Commits](https://www.conventionalcommits.org/) 형식을 사용해 커밋만으로 **변경의 종류·범위·영향**을 파악하고, 자동 changelog/버전 산정의 기반을 만든다.

---

## 1. 기본 형식

```text
<type>(<scope>): <subject>
<빈 줄>
<body (선택)>
<빈 줄>
<footer (선택)>
```

- `type`: 변경 종류(아래 표). **필수**.
- `scope`: 영향 범위(도메인/모듈). 선택. 예: `user`, `order`, `auth`.
- `subject`: 한 줄 요약. **필수**.
- `body`: 변경 이유/맥락(무엇을 바꿨는지보다 **왜**). 선택.
- `footer`: 이슈 참조, `BREAKING CHANGE`. 선택.

### subject 작성 규칙

| 규칙 | 좋은 예 | 나쁜 예 |
| --- | --- | --- |
| 명령문(동사 원형)으로 시작 | `add login api` | `added login api`, `adds login api` |
| 50자 이내 권장 | `fix null pointer in order cancel` | (장문 한 줄) |
| 끝에 마침표 없음 | `update api convention` | `update api convention.` |
| 무엇을 했는지 명확히 | `fix order cancel race condition` | `fix bug`, `wip`, `update` |

> 한국어/영어는 팀 합의로 통일한다. 본 base repository는 **subject 영어, body 한국어 허용**을 기본 예시로 둔다.

---

## 2. type 표

| type | 용도 | SemVer 영향(예시) |
| --- | --- | --- |
| `feat` | 기능 추가 | minor |
| `fix` | 버그 수정 | patch |
| `docs` | 문서만 변경 | 없음 |
| `style` | 포맷/세미콜론 등 동작 무관 변경 | 없음 |
| `refactor` | 동작 변화 없는 구조 개선 | 없음 |
| `test` | 테스트 추가/수정 | 없음 |
| `chore` | 빌드/설정/의존성 등 잡무 | 없음 |
| `perf` | 성능 개선 | patch |
| `ci` | CI 설정 변경 | 없음 |
| `revert` | 이전 커밋 되돌리기 | 상황에 따름 |

> 브랜치 `type`(feat/fix/chore/docs/refactor/test)과 정렬되며, 커밋에서는 `style`/`perf`/`ci`/`revert`를 추가로 사용할 수 있다.

---

## 3. 예시 커밋 메시지

### 단순 (subject만)

```text
feat(auth): add jwt login api
```

```text
fix(order): prevent double cancel on concurrent requests
```

```text
docs(convention): add commit convention guide
```

```text
chore(deps): bump spring-boot to 3.3.1
```

### body 포함

```text
fix(order): prevent double payment on retry

결제 재시도 시 동일 주문이 두 번 결제되던 문제를 수정한다.
idempotency key를 기준으로 중복 요청을 무시하도록 변경했다.

Refs: #142
```

### footer + 이슈 종료

```text
feat(user): support nickname change

Closes: #210
```

---

## 4. BREAKING CHANGE (본문/푸터 규칙)

호환 불가 변경은 두 가지 방법 중 하나로 명시한다.

1. type/scope 뒤에 `!` 표기
2. footer에 `BREAKING CHANGE:` 블록 작성

```text
feat(api)!: change user id type from int to uuid

기존 정수 id를 UUID로 변경한다. 클라이언트는 id 파싱 로직을 수정해야 한다.

BREAKING CHANGE: GET /api/v1/users/{id}의 id가 정수에서 UUID로 변경됨.
v1 클라이언트는 v2 마이그레이션이 필요하다. 마이그레이션 가이드: docs/api/migration.md
```

- `BREAKING CHANGE:`가 있으면 SemVer **major**를 올린다.
- API breaking change는 [api-convention](./api-convention.md) §7의 versioning/migration plan과 연계한다.

---

## 5. 좋은 커밋 단위

- **하나의 커밋 = 하나의 논리적 변경**. 무관한 변경을 한 커밋에 섞지 않는다.
- 포맷팅과 로직 변경을 분리한다(리뷰 노이즈 감소).
- 작업 도중의 `wip`, `fixup` 커밋은 머지 전 정리한다(Squash로 압축됨, [branch-convention](./branch-convention.md) §4).

| 좋은 예 | 나쁜 예 |
| --- | --- |
| `feat(order): add cancel api` + `test(order): add cancel tests` (2커밋) | `feat: add cancel api and reformat whole project` |
| `refactor(user): extract mapper` | `fix stuff` |

---

## 6. AI 생성 커밋 표기 (선택)

도구가 생성/보조한 커밋은 footer에 공동 작성자를 표기할 수 있다(팀 정책에 따름).

```text
Co-Authored-By: <도구명> <noreply@example.com>
```

> Secret/토큰을 커밋 메시지에 포함하지 않는다([.claude/rules/security.md](../../.claude/rules/security.md)).

---

## 체크리스트

- [ ] `<type>(<scope>): <subject>` 형식을 따랐는가
- [ ] `type`이 허용 표(feat/fix/docs/...) 중 하나인가
- [ ] subject가 명령문, 50자 이내, 마침표 없음인가
- [ ] 한 커밋이 하나의 논리적 변경인가
- [ ] 호환 불가 변경에 `!` 또는 `BREAKING CHANGE:`를 명시했는가
- [ ] 커밋 메시지에 secret/토큰이 없는가
