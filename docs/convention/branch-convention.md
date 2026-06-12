# Branch Convention

> 모든 작업은 **GitHub Issue 생성 → 이슈 번호를 포함한 브랜치 생성** 순서로 시작한다.
> 연계 문서: [commit-convention](./commit-convention.md), [collaboration-convention](./collaboration-convention.md)(PR 작성·리뷰 규칙 포함)

## 목적

브랜치 이름만 보고 **어떤 이슈의, 어떤 종류의 작업인지** 추적할 수 있게 하고,
이슈 → 브랜치 → PR → 머지로 이어지는 작업 흐름을 일관되게 유지한다.

---

## 1. 작업 시작 규칙 — Issue 먼저

1. 작업 전에 **GitHub Issue를 먼저 생성**한다([.github/ISSUE_TEMPLATE](../../.github/ISSUE_TEMPLATE/) 양식 사용).
2. 발급된 **이슈 번호를 브랜치명에 포함**한다.
3. PR 본문에 `Closes #<이슈번호>`를 적어, 머지 시 이슈가 자동으로 닫히게 한다.

> 이슈 없는 브랜치는 만들지 않는다. 작은 작업도 이슈로 등록해 추적한다.

---

## 2. 브랜치 네이밍 규칙

```text
<type>/<issue-number>-<short-description>

예) feature/12-user-login-api
    fix/45-order-null-pointer
    docs/7-branch-convention
```

- `type`: 아래 type 목록의 값만 사용한다.
- `issue-number`: 해당 작업의 GitHub 이슈 번호. **필수.**
- `short-description`: 영문 소문자 + 하이픈(kebab-case), 2~4단어 권장.
- 한글, 공백, 대문자, 언더스코어(`_`), `#`은 사용하지 않는다.

### 좋은 예 / 나쁜 예

| 좋은 예 | 나쁜 예 | 이유 |
| --- | --- | --- |
| `feature/12-user-login-api` | `feature/user-login-api` | 이슈 번호 누락 |
| `fix/45-login-token-expiry` | `fix/#45-login-token-expiry` | `#` 사용 (숫자만 쓴다) |
| `docs/7-pr-convention` | `Docs/7-PR_Convention` | 대문자/언더스코어 사용 |
| `refactor/23-user-mapper` | `refactor/유저매퍼` | 한글 사용, 이슈 번호 누락 |

---

## 3. type 목록

| type | 용도 | 예시 브랜치명 |
| --- | --- | --- |
| `feature` | 새로운 기능 추가 | `feature/12-user-login-api` |
| `fix` | 버그 수정 | `fix/45-order-null-pointer` |
| `refactor` | 동작 변화 없는 내부 구조 개선 | `refactor/23-user-mapper` |
| `docs` | 문서만 변경 | `docs/7-api-design-guide` |
| `test` | 테스트 추가/수정 | `test/31-order-edge-cases` |
| `chore` | 빌드/설정/의존성 등 잡무 | `chore/9-ci-cache` |
| `hotfix` | 운영 긴급 수정 (`main`에서 분기) | `hotfix/52-payment-rollback` |
| `release` | 배포 준비 (필요 시) | `release/1.2.0` |

> `release`/`hotfix`는 원본 저장소(upstream)의 공용 브랜치로 운영한다([collaboration-convention](./collaboration-convention.md) §3). `release`는 이슈 번호 대신 버전을 사용한다.

---

## 4. 기준 브랜치 (Base Branches)

| 브랜치 | 역할 | 보호 |
| --- | --- | --- |
| `main` | 배포 가능한 안정 브랜치 | 직접 push 금지, PR 필수 |
| `develop` | 개발 통합 브랜치 (feature PR의 base) | 직접 push 금지, PR 필수 |

- feature 브랜치는 **`develop`에서 분기**하고, PR도 `develop`으로 보낸다.
- 개인 작업 브랜치는 원본이 아닌 **개인 fork에만** 둔다([collaboration-convention](./collaboration-convention.md)).

---

## 5. 머지 전략

| 전략 | 사용 시점 | 특징 |
| --- | --- | --- |
| **Squash and merge** *(기본)* | feature/fix → `develop` PR | 이슈 단위 커밋 1개로 압축, 히스토리 깔끔 |
| Create a merge commit | `develop` → `main` 등 공용 브랜치 통합 | 분기 이력 보존 |

- Squash 시 최종 커밋 제목은 PR 제목이 되므로, PR 제목은 [commit-convention](./commit-convention.md) 형식을 따른다.
- 병합 후 원격 feature 브랜치는 삭제한다(자동 삭제 설정 권장).

---

## 6. 수명 주기 (Lifecycle)

```text
1. 이슈 생성          GitHub Issue 등록 → 이슈 번호 확보 (예: #12)
2. develop 동기화     git checkout develop && git fetch upstream && git merge upstream/develop
3. 브랜치 생성        git checkout -b feature/12-user-login-api
4. 작업 + 커밋        commit-convention 준수
5. fork로 push        git push origin feature/12-user-login-api
6. PR 생성            base: upstream/develop, 본문에 Closes #12
7. 리뷰 통과 후 머지  Squash and merge → 이슈 자동 종료
8. 브랜치 삭제        로컬/원격 삭제 후 develop 동기화
```

---

## 체크리스트

- [ ] 작업 전에 GitHub Issue를 생성했다
- [ ] 브랜치명이 `<type>/<issue-number>-<short-description>` 형식이다
- [ ] `type`이 허용 목록 중 하나다
- [ ] 설명이 영문 소문자 kebab-case다
- [ ] PR 본문에 `Closes #이슈번호`로 이슈를 연결했다
- [ ] 병합 후 로컬/원격 브랜치를 삭제했다
