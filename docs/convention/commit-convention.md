# Commit Convention

> [Conventional Commits 1.0.0](https://www.conventionalcommits.org/ko/v1.0.0/) 표준을 따른다.
> 연계 문서: [branch-convention](./branch-convention.md), [collaboration-convention](./collaboration-convention.md)(PR 작성·리뷰 규칙 포함)

## 목적

커밋 메시지만으로 **변경의 종류·범위·영향**을 파악하고, 이슈/PR과 연결해 변경 이력을
추적 가능하게 한다. 자동 changelog 생성과 버전 산정(SemVer)의 기반이 된다.

---

## 1. 기본 형식

```text
<type>(<scope>): <subject>
<빈 줄>
<body (선택)>
<빈 줄>
<footer (선택)>
```

| 요소 | 필수 | 규칙 |
| --- | --- | --- |
| `type` | O | 변경 종류. 아래 type 표의 값만 사용 |
| `scope` | X | 영향 범위(도메인/모듈). 예: `auth`, `order`, `config` |
| `subject` | O | 한 줄 요약. **50자 이내, 끝에 마침표 없음** |
| `body` | X | "무엇을"보다 **"왜"** 를 설명. 한 줄 72자 내 줄바꿈 권장 |
| `footer` | X | 이슈 참조(`Refs: #12`, `Closes: #12`), `BREAKING CHANGE:` |

### subject 작성 규칙

| 규칙 | 좋은 예 | 나쁜 예 |
| --- | --- | --- |
| 무엇을 했는지 구체적으로 | `fix(order): 동시 취소 경합 조건 수정` | `fix: 버그수정`, `update`, `wip` |
| 50자 이내, 마침표 없음 | `feat(auth): JWT 로그인 API 추가` | `...추가했습니다.` |
| 명령형/명사형 종결로 통일 | `~추가`, `~수정`, `~제거` | `~추가함`, `~수정했음` 혼용 |

> 언어는 팀이 하나로 통일한다. **본 저장소는 한국어 subject를 기본**으로 한다(영어로 쓸 경우 동사 원형 시작: `add login api`).

---

## 2. type 표

Conventional Commits에서 널리 쓰이는 표준 type을 사용한다.

| type | 용도 | SemVer 영향 |
| --- | --- | --- |
| `feat` | 새로운 기능 추가 | minor |
| `fix` | 버그 수정 | patch |
| `docs` | 문서만 변경 | — |
| `style` | 포맷/공백 등 동작 무관 변경 | — |
| `refactor` | 동작 변화 없는 구조 개선 | — |
| `perf` | 성능 개선 | patch |
| `test` | 테스트 추가/수정 | — |
| `build` | 빌드 시스템/외부 의존성 변경 | — |
| `ci` | CI 설정/스크립트 변경 | — |
| `chore` | 그 외 잡무(설정, 정리 등) | — |
| `revert` | 이전 커밋 되돌리기 | 상황에 따름 |

> 브랜치 type(`feature`/`fix`/... — [branch-convention](./branch-convention.md))과 커밋 type은 별개다.
> 예: `feature/12-user-login-api` 브랜치의 커밋은 `feat(auth): ...`.

---

## 3. 예시

### subject만

```text
feat(auth): JWT 로그인 API 추가
```

```text
fix(order): 동시 요청 시 중복 취소 방지
```

```text
docs(convention): 커밋 컨벤션 가이드 작성
```

### body + 이슈 참조

```text
feat(order): 주문 생성 API 구현

주문 생성 시 재고를 검증하고 결제 대기 상태로 저장한다.
재고 검증 실패는 표준 에러 응답(422)으로 변환한다.

Refs: #12
```

> 이슈 종료(`Closes #12`)는 PR 본문에서 연결하는 것을 기본으로 하고,
> 커밋 footer에는 `Refs: #12`로 참조만 남긴다.

---

## 4. BREAKING CHANGE

호환되지 않는 변경은 두 가지 방법 중 하나(또는 둘 다)로 명시한다. SemVer **major**를 올린다.

1. type/scope 뒤에 `!` 표기
2. footer에 `BREAKING CHANGE:` 블록 작성

```text
feat(api)!: 사용자 ID 타입을 정수에서 UUID로 변경

BREAKING CHANGE: GET /api/v1/users/{id}의 id가 정수에서 UUID로 변경됨.
클라이언트는 id 파싱 로직을 수정해야 한다.
```

---

## 5. 커밋 단위

- **하나의 커밋 = 하나의 논리적 변경.** 무관한 변경을 한 커밋에 섞지 않는다.
- 포맷팅(`style`)과 로직 변경(`feat`/`fix`)을 분리한다(리뷰 노이즈 감소).
- 작업 도중의 `wip`, `fixup` 커밋은 머지 전 정리한다(Squash merge로 압축됨, [branch-convention](./branch-convention.md) §5).

| 좋은 예 | 나쁜 예 |
| --- | --- |
| `feat(order): 취소 API 추가` + `test(order): 취소 테스트 추가` (2커밋) | `feat: 취소 API 추가 및 전체 포맷팅` |
| `refactor(user): mapper 분리` | `fix stuff` |

---

## 체크리스트

- [ ] `<type>(<scope>): <subject>` 형식을 따랐다
- [ ] type이 표준 type 표의 값이다
- [ ] subject가 50자 이내, 마침표 없음, 구체적이다
- [ ] 관련 이슈를 footer(`Refs:`) 또는 PR(`Closes #N`)에서 참조했다
- [ ] 호환 불가 변경에 `!` 또는 `BREAKING CHANGE:`를 명시했다
- [ ] 한 커밋이 하나의 논리적 변경만 담는다
- [ ] 커밋 메시지에 Secret/토큰이 없다
