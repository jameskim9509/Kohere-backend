# Collaboration Convention (Fork 기반 PR 워크플로우)

> 이 프로젝트는 **Fork 기반 PR 협업 방식**으로 진행한다.
> 팀원은 원본 저장소에 직접 push하지 않고, 개인 fork에서 feature 브랜치를 만들어 작업한 뒤 원본 저장소로 Pull Request를 보낸다.
> PR 작성·리뷰·머지 규칙은 [§5](#5-pr-작성리뷰-규칙)에서 함께 다룬다.
> 연계 문서: [branch-convention](./branch-convention.md), [commit-convention](./commit-convention.md) · PR 본문 양식: [.github/PULL_REQUEST_TEMPLATE.md](../../.github/PULL_REQUEST_TEMPLATE.md)

## 목적

- 원본 저장소(upstream)에 대한 직접 변경을 최소화하고, 모든 변경을 **PR + 리뷰 + CI**를 거치게 한다.
- 개인 작업 브랜치는 각자의 fork에서 관리해 원본 저장소를 깨끗하게 유지한다.
- 권한을 최소화하고, 머지 권한자만 원본에 반영한다.

---

## 1. Fork 기반 PR 방식을 쓰는 이유

- **원본 저장소 영향 최소화**: 팀원은 원본에 직접 push하지 않고, fork의 feature 브랜치에서 작업한다. 원본에는 PR로만 반영한다.
- **개인별 작업 격리**: 각자의 fork에서 feature 브랜치를 자유롭게 만들고 지운다.
- **원본 브랜치 수 최소화**: 원본에는 공용 브랜치(`main`/`develop`/`release/*`/`hotfix/*`)만 둔다. 일반 feature 브랜치는 fork에만 둔다.
- **권한 최소화**: 원본 저장소에 직접 push 권한을 주지 않는다.

---

## 2. 기본 용어

| 용어           | 의미                                                             | 예시                        |
| -------------- | ---------------------------------------------------------------- | --------------------------- |
| upstream       | 팀 공유 원본 저장소                                              | `team/project`            |
| origin         | 개인이 fork한 자신의 저장소                                      | `member/project`          |
| fork           | 원본을 개인 공간으로 복사한 저장소                               | —                          |
| feature branch | 기능 단위 작업 브랜치(개인 fork에 생성,**이슈 번호 포함**) | `feature/12-order-create` |
| Pull Request   | fork의 feature 브랜치를 원본 develop/main에 반영 요청            | —                          |

---

## 3. 브랜치 전략

**원본 저장소(upstream)** — 공용 브랜치만 유지한다.

```text
upstream
├── main        # 배포 가능한 안정 브랜치
├── develop     # 개발 통합 브랜치
├── release/*   # 배포 준비 브랜치 (필요할경우)
└── hotfix/*    # 긴급 수정 브랜치 (필요할경우)
```

**개인 fork(origin)** — feature 브랜치는 여기서 관리한다.

```text
origin
├── main
├── develop
└── feature/*   # 기능 단위 작업 브랜치
```

흐름:

- 기능 개발: `origin/feature/*` → PR → `upstream/develop`
- 배포: `upstream/develop` → `release/*` → `upstream/main`
- 긴급 수정: `upstream/main` → `hotfix/*` → `upstream/main` 및 `upstream/develop` 반영

> 모든 작업은 **GitHub Issue를 먼저 생성**한 뒤 시작한다. 브랜치 네이밍(`<type>/<이슈번호>-<설명>`)은 [branch-convention](./branch-convention.md)을 따른다.

---

## 4. 협업 플로우

### 4-1. 최초 설정 (1회)

1. GitHub에서 원본 저장소를 fork한다. (`team/project` → `member/project`)
2. 개인 fork를 로컬에 clone한다.

```bash
git clone https://github.com/<me>/<repo>.git
cd <repo>
```

3. 원본 저장소를 `upstream`으로 등록한다.

```bash
git remote add upstream https://github.com/<team>/<repo>.git
git remote -v
# origin    https://github.com/<me>/<repo>.git
# upstream  https://github.com/<team>/<repo>.git
```

### 4-2. 작업 시작 전 동기화

개발 기준 브랜치는 `upstream/develop`이다.

```bash
git checkout develop
git fetch upstream
git merge upstream/develop
git push origin develop
```

### 4-3. 이슈 생성 → feature 브랜치 생성 및 작업

작업 전에 **GitHub Issue를 먼저 생성**해 이슈 번호를 확보한다(예: `#12`).
브랜치명에는 이슈 번호를 포함한다([branch-convention](./branch-convention.md) 참고).

```bash
git checkout develop
git checkout -b feature/12-order-create

# 작업 후 커밋 (commit-convention 준수)
git add .
git commit -m "feat(order): 주문 생성 API 구현"

# 개인 fork(origin)로 push
git push origin feature/12-order-create
```

### 4-4. PR 생성

GitHub에서 다음과 같이 PR을 만든다.

| 항목            | 값                          |
| --------------- | --------------------------- |
| base repository | `team/project`            |
| base branch     | `develop`                 |
| head repository | `member/project`          |
| compare branch  | `feature/12-order-create` |

즉 `member/project:feature/12-order-create` → `team/project:develop`.

PR 본문에는 `Closes #12`를 적어 머지 시 이슈가 자동으로 닫히게 한다.

### 4-5. 리뷰 & merge

리뷰어가 다음을 확인한 뒤 머지한다. (상세 기준은 [§5](#5-pr-작성리뷰-규칙))

- 요구사항을 만족하는 기능인지
- 코드 컨벤션을 지켰는지
- 테스트가 있는지 / CI가 통과하는지
- 충돌이 없는지 / 리뷰 코멘트가 해결됐는지

### 4-6. merge 후 fork 최신화

```bash
git checkout develop
git fetch upstream
git merge upstream/develop
git push origin develop
```

- **작업 브랜치(`feature/*`)는 삭제하지 않고 보존한다.** Squash 머지로 develop에는 압축 커밋
  1개만 남으므로, 원본 커밋 이력은 feature 브랜치에서 확인한다([branch-convention](./branch-convention.md) §5).
- develop이 upstream과 갈라진 경우(develop 직접 커밋 등)에는 merge 대신
  `git reset --hard upstream/develop` 후 `git push --force-with-lease origin develop`으로 맞춘다.

> GitHub UI의 **Sync fork** 기능을 사용해도 된다.

---

## 5. PR 작성·리뷰 규칙

### 5-1. PR 제목

Squash merge 시 **PR 제목이 develop의 최종 커밋 메시지가 된다**([branch-convention](./branch-convention.md) §5).
따라서 PR 제목은 [commit-convention](./commit-convention.md)의 커밋 형식을 그대로 따른다.

```text
<type>(<scope>): <subject>

예) feat(order): 주문 생성 API 구현
    fix(auth): 토큰 만료 시간 계산 오류 수정
```

- type/scope/subject 규칙은 commit-convention과 동일하다(50자 이내, 마침표 없음, 구체적으로).
- `버그 수정`, `코드 정리` 같은 모호한 제목은 쓰지 않는다.

### 5-2. PR 본문

[.github/PULL_REQUEST_TEMPLATE.md](../../.github/PULL_REQUEST_TEMPLATE.md) 양식을 따른다.

- **변경 목적에는 "왜"를 쓴다.** 무엇을 바꿨는지는 diff가 보여주지만, 왜 바꿨는지는 본문에만 남는다.
- `Closes #이슈번호`로 이슈를 연결한다(머지 시 자동 종료).
- API 응답·화면 등 눈으로 확인하는 변경은 before/after 캡처 또는 실행 결과를 첨부한다.

### 5-3. PR 크기

- **변경 400줄 이하를 권장**한다. 넘을 것 같으면 이슈를 쪼개서 PR을 분할한다.
- 하나의 PR은 **하나의 이슈(하나의 자기완결적 변경)** 만 다룬다. 무관한 리팩토링·포맷팅을 섞지 않는다.
- 예외: 파일 삭제, 자동 생성 코드(lock 파일 등), 일괄 rename은 줄 수에서 제외하되 본문에 명시한다.

> 근거: 리뷰 분량이 200~400줄일 때 결함 발견율이 가장 높다는 연구(Cisco/SmartBear)와
> Google 엔지니어링 가이드 [Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html).

### 5-4. 리뷰 진행 규칙

- 리뷰 요청을 받으면 **24시간(영업일 기준) 내 첫 응답**을 목표로 한다.
- 작성자는 모든 코멘트에 반응한다 — 반영하거나, 반영하지 않는 이유를 남긴다.
- 리뷰는 코드에 대해 말하고 사람을 평가하지 않는다. 좋은 부분은 칭찬 코멘트로 남긴다.
- 승인 후 새 커밋이 push되면 기존 승인은 무효화된다(Ruleset, [§7-2](#7-2-ruleset)).

#### Pn 룰 — 코멘트 강도 표시

코멘트 앞에 `P1`~`P5`를 붙여 요구 강도를 명시한다. 받는 사람이 모든 코멘트에
같은 무게로 대응하느라 소모되는 것을 막고, 중요한 지적에 집중하게 한다.
(출처: [코드 리뷰 in 뱅크샐러드 — 4년 간의 변천사](https://blog.banksalad.com/tech/banksalad-code-review-culture/))

| 라벨   | 의미                     | 효력                |
| ------ | ------------------------ | ------------------- |
| `P1` | 꼭 반영해주세요          | 반영 전 머지 불가   |
| `P2` | 적극적으로 고려해주세요  | 미반영 시 사유 설명 |
| `P3` | 가능하면 반영해주세요    | 작성자 판단         |
| `P4` | 반영해도, 안 해도 좋아요 | 참고                |
| `P5` | 사소한 의견입니다        | 의견                |

예) `P1: 이 쿼리는 N+1이 발생합니다. fetch join으로 변경해주세요.`

### 5-5. 머지 조건 (Merge Gate)

아래를 모두 만족해야 머지한다. 대부분 Ruleset([§7-2](#7-2-ruleset))으로 강제된다.

| 조건                                    | 강제 수단    |
| --------------------------------------- | ------------ |
| 1명 이상 approve                        | Ruleset      |
| 모든 리뷰 코멘트(conversation) resolved | Ruleset      |
| CI 통과                                 | Ruleset / CI |
| base(`develop`)와 충돌 없음           | GitHub       |
| `P1`/`P2` 코멘트 반영 또는 합의     | 리뷰어 확인  |

- 머지 방식은 **Squash and merge**를 기본으로 한다([branch-convention](./branch-convention.md) §5).
- 머지는 approve한 리뷰어 또는 maintainer가 수행한다.

---

## 6. 리더/Maintainer도 fork로 작업

머지 권한이 있는 리더도 원본에 직접 push하지 않고 개인 fork에서 PR을 올린다.

```text
leader/project:feature/34-payment-confirm → PR → team/project:develop
```

- 팀 규칙을 동일하게 적용하기 위해
- 변경을 PR 단위로 기록하고 리뷰·CI를 거치기 위해
- 원본 브랜치 직접 수정 가능성을 줄이기 위해

---

## 7. GitHub 권한 & Ruleset 설정

### 7-1. 권한

- 원본 저장소에는 **직접 push 권한을 주지 않는다.**
- 접근 제어는 Branch protection 대신 **Ruleset**으로 관리한다.

### 7-2. Ruleset

경로: `Repository → Settings → Rules → Rulesets → New ruleset → New branch ruleset`

| 항목               | 값                  |
| ------------------ | ------------------- |
| 이름               | Protect branch      |
| Enforcement status | Active              |
| Target branches    | `*` (모든 브랜치) |
| Bypass list        | 비움                |

적용 규칙(Rules):

- **Require a pull request before merging** — main/develop 직접 push 금지, PR 필수
- **Require approvals** — 1명 이상 승인
- **Dismiss stale pull request approvals when new commits are pushed** — 승인 후 새 커밋이 추가되면 기존 승인 무효화
- **Require conversation resolution before merging** — 리뷰 대화가 모두 해결되어야 머지
- **Block force pushes** — main/develop force push 차단
- **Restrict deletions** — main/develop 브랜치 삭제 차단

> Target을 `*`로 두면 새로 생기는 feature 브랜치도 보호 대상이 되어, 원본 내부에서 feature → develop PR을 만들지 않고 fork 기반으로만 작업하도록 유도한다.

---

## 체크리스트

- [ ] 원본을 fork하고 로컬에 clone했으며 `upstream`을 등록했다
- [ ] 작업 전 GitHub Issue를 생성해 이슈 번호를 확보했다
- [ ] 작업 전 `upstream/develop`을 동기화했다
- [ ] 이슈 번호를 포함한 feature 브랜치를 개인 fork에 만들고 작업했다
- [ ] PR base는 `upstream/develop`, head는 `fork/feature/*`로 지정하고 `Closes #이슈번호`로 이슈를 연결했다
- [ ] PR 제목이 commit-convention 형식(`<type>(<scope>): <subject>`)이다
- [ ] PR이 하나의 이슈만 다루고, 400줄 이하이거나 예외 사유를 본문에 적었다
- [ ] 모든 리뷰 코멘트에 반응했고 `P1`/`P2`를 반영하거나 합의했다
- [ ] 원본 `main`/`develop`에 직접 push하지 않았다
- [ ] merge 후 fork develop을 최신화했다 (feature 브랜치는 보존)
