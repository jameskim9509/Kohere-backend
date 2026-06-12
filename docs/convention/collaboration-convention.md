# Collaboration Convention (Fork 기반 PR 워크플로우)

> 이 프로젝트는 **Fork 기반 PR 협업 방식**으로 진행한다.
> 팀원은 원본 저장소에 직접 push하지 않고, 개인 fork에서 feature 브랜치를 만들어 작업한 뒤 원본 저장소로 Pull Request를 보낸다.
> 연계 문서: [branch-convention](./branch-convention.md), [commit-convention](./commit-convention.md), [pr-convention](./pr-convention.md)

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

| 용어           | 의미                                                  | 예시                     |
| -------------- | ----------------------------------------------------- | ------------------------ |
| upstream       | 팀 공유 원본 저장소                                   | `team/project`         |
| origin         | 개인이 fork한 자신의 저장소                           | `member/project`       |
| fork           | 원본을 개인 공간으로 복사한 저장소                    | —                       |
| feature branch | 기능 단위 작업 브랜치(개인 fork에 생성)               | `feature/order-create` |
| Pull Request   | fork의 feature 브랜치를 원본 develop/main에 반영 요청 | —                       |

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

> 브랜치 네이밍(`<type>/<설명>`)은 [branch-convention](./branch-convention.md)을 따른다.

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

### 4-3. feature 브랜치 생성 및 작업

```bash
git checkout develop
git checkout -b feature/order-create

# 작업 후 커밋 (commit-convention 준수)
git add .
git commit -m "feat: 주문 생성 API 구현"

# 개인 fork(origin)로 push
git push origin feature/order-create
```

### 4-4. PR 생성

GitHub에서 다음과 같이 PR을 만든다.

| 항목            | 값                       |
| --------------- | ------------------------ |
| base repository | `team/project`         |
| base branch     | `develop`              |
| head repository | `member/project`       |
| compare branch  | `feature/order-create` |

즉 `member/project:feature/order-create` → `team/project:develop`.

### 4-5. 리뷰 & merge

리뷰어가 다음을 확인한 뒤 머지한다. (상세 기준은 [pr-convention](./pr-convention.md))

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

# 작업 브랜치 삭제
git branch -d feature/order-create
git push origin --delete feature/order-create
```

> GitHub UI의 **Sync fork** 기능을 사용해도 된다.

---

## 5. 리더/Maintainer도 fork로 작업

머지 권한이 있는 리더도 원본에 직접 push하지 않고 개인 fork에서 PR을 올린다.

```text
leader/project:feature/payment-confirm → PR → team/project:develop
```

- 팀 규칙을 동일하게 적용하기 위해
- 변경을 PR 단위로 기록하고 리뷰·CI를 거치기 위해
- 원본 브랜치 직접 수정 가능성을 줄이기 위해

---

## 6. GitHub 권한 & Ruleset 설정

### 6-1. 권한

- 원본 저장소에는 **직접 push 권한을 주지 않는다.**
- 접근 제어는 Branch protection 대신 **Ruleset**으로 관리한다.

### 6-2. Ruleset

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
- [ ] 작업 전 `upstream/develop`을 동기화했다
- [ ] feature 브랜치를 개인 fork에 만들고 작업했다
- [ ] PR base는 `upstream/develop`, head는 `fork/feature/*`로 지정했다
- [ ] 원본 `main`/`develop`에 직접 push하지 않았다
- [ ] merge 후 fork를 최신화하고 작업 브랜치를 삭제했다
