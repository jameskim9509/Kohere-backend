# Branch Convention

> **예시(Spring Boot 기준) 안내**
> 브랜치 규칙 자체는 스택과 무관하게 동일하게 적용됩니다. 아래 브랜치명 예시만 도메인에 맞게 교체하세요.
> 관련 규칙: [.claude/rules/git-workflow.md](../../.claude/rules/git-workflow.md)
> 연계 문서: [commit-convention](./commit-convention.md), [pr-convention](./pr-convention.md)

## 목적

브랜치 이름만 보고 **작업 종류와 의도**를 파악할 수 있게 하고, 머지 전략을 통일해 히스토리를 깔끔하게 유지한다.

---

## 1. 브랜치 네이밍 규칙

```text
<type>/<short-description>

예) feat/user-login-api
    fix/order-null-pointer
    docs/api-convention
```

- 형식: `<type>/<short-description>`
- `type`은 아래 표의 값만 사용한다.
- `short-description`은 **영문 소문자 + 하이픈(kebab-case)**, 2~4단어 권장.
- 이슈 트래커를 쓰면 prefix에 번호를 둔다: `feat/123-user-login-api`.
- 한글, 공백, 대문자, 언더스코어(`_`)는 사용하지 않는다.

---

## 2. type 목록

| type | 용도 | 예시 브랜치명 |
| --- | --- | --- |
| `feat` | 새로운 기능 추가 | `feat/user-login-api` |
| `fix` | 버그 수정 | `fix/order-null-pointer` |
| `chore` | 빌드/설정/의존성 등 잡무 (기능·테스트 영향 없음) | `chore/bump-spring-boot-3-3` |
| `docs` | 문서만 변경 | `docs/branch-convention` |
| `refactor` | 동작 변화 없는 내부 구조 개선 | `refactor/order-service-split` |
| `test` | 테스트 추가/수정 | `test/order-service-edge-cases` |

> **가정:** 위 6개 type을 기본으로 한다. `hotfix`(운영 긴급 수정), `release`(릴리스 준비)가 필요하면 팀 합의 후 추가한다.

### 좋은 예 / 나쁜 예

| 좋은 예 | 나쁜 예 | 이유 |
| --- | --- | --- |
| `feat/payment-webhook` | `feature/Payment_Webhook` | type 오타, 대문자/언더스코어 |
| `fix/login-token-expiry` | `fix` | 설명 누락 |
| `docs/pr-convention` | `update-docs` | type 누락 |
| `refactor/user-mapper` | `refactor/유저매퍼` | 한글 사용 |
| `chore/ci-cache` | `chore/ci cache` | 공백 사용 |

---

## 3. 기준 브랜치(Base Branches)

| 브랜치 | 역할 | 보호 |
| --- | --- | --- |
| `main` | 배포 가능한 안정 상태(기본 브랜치) | 직접 push 금지, PR 필수 |
| `develop` *(선택)* | 통합 개발 브랜치 | 팀 규모가 커지면 도입 |

> 본 base repository는 단일 `main` 트렁크 기반을 기본으로 한다. 릴리스 흐름이 복잡해지면 `develop`/`release/*` 도입을 ADR로 기록한다([docs/adr](../adr/index.md)).

---

## 4. 머지 전략

| 전략 | 사용 시점 | 특징 |
| --- | --- | --- |
| **Squash and merge** *(기본)* | feature/fix 브랜치를 `main`에 병합 | 작업 단위 커밋 1개로 압축 → 히스토리 깔끔 |
| **Rebase and merge** | 의미 있는 커밋을 보존하고 싶을 때 | 선형 히스토리, merge commit 없음 |
| **Create a merge commit** | 릴리스/장기 브랜치 통합 | 브랜치 분기 이력 보존 |

```text
main:    o---o---o------------o (squash merge)
                  \          /
feat:              o--o--o--o   ← 여러 작업 커밋이 1개로 압축
```

규칙:
- `main` 병합은 **Squash and merge**를 기본으로 한다.
- Squash 시 최종 커밋 제목은 [commit-convention](./commit-convention.md)을 따른다.
- 병합 후 원격 브랜치는 삭제한다(자동 삭제 권장).
- 머지 전 `main`의 최신 변경을 반영(rebase 또는 merge)하고 충돌을 해소한다.

---

## 5. 수명 주기(Lifecycle)

```text
1. main 최신화        git switch main && git pull
2. 브랜치 생성        git switch -c feat/user-login-api
3. 작업 + 커밋        (commit-convention 준수)
4. 원격 push          git push -u origin feat/user-login-api
5. PR 생성            (pr-convention 준수)
6. 리뷰 통과 후 머지  Squash and merge
7. 브랜치 삭제        병합 시 자동 삭제
```

---

## 체크리스트

- [ ] 브랜치명이 `<type>/<short-description>` 형식인가
- [ ] `type`이 허용 목록(feat/fix/chore/docs/refactor/test) 중 하나인가
- [ ] 설명이 영문 소문자 + 하이픈(kebab-case)인가
- [ ] `main`에 직접 push 하지 않고 PR을 거치는가
- [ ] 병합 전략(기본 Squash and merge)을 따랐는가
- [ ] 병합 후 원격 브랜치를 삭제했는가
