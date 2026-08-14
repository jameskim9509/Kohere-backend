# Backend Base Repository Template

아직 어떤 백엔드 서비스를 만들지 정하지 않은 상태에서 사용할 수 있는 **backend base repository**입니다.

이 저장소는 다음을 미리 갖춰두는 것을 목표로 합니다.

- 백엔드 프로젝트 공통 폴더 구조
- GitHub Issue/PR/CI 기본 템플릿
- 컨벤션/설계/요구사항/ADR 문서 템플릿
- Claude Code 권한·보안 설정

> **기술 스택:** **Java 21 · Spring Boot 4.1 · Spring Modulith 2.1 · Gradle 9.5** (모듈러 모놀리식 + DDD 계층, [code-style](docs/convention/code-style.md) 참고). 배포는 **M0–M6 전 구간 로컬 컨테이너(docker-compose) 기반으로 개발하고 M7에서 AWS로 이전·배포**합니다(동일 Docker 이미지, 인프라만 로컬↔매니지드 교체 — [project-brief §7 마일스톤](docs/project/project-brief.md#7-마일스톤-milestones)). DB는 결정 사항이 확정 시 관련 문서를 갱신합니다.

## 사용 방법

```bash
git clone <this-template-repository> <project-name>
cd <project-name>
claude
```

## 협업 방식

이 저장소는 **Fork 기반 PR 워크플로우**로 협업합니다. 원본 저장소(upstream)에 직접 push하지 않고, **GitHub Issue를 먼저 생성한 뒤** 개인 fork에서 이슈 번호를 포함한 feature 브랜치로 작업하고 PR로 반영합니다. 전체 규칙·GitHub Ruleset 설정은 [collaboration-convention](docs/convention/collaboration-convention.md)을 참고하세요.

### 처음 한 번 (Setup)

```bash
# 1) GitHub에서 원본 저장소를 fork
# 2) 내 fork를 clone
git clone https://github.com/<me>/<repo>.git
cd <repo>
# 3) 원본을 upstream으로 등록
git remote add upstream https://github.com/<team>/<repo>.git
```

### 기능 작업할 때마다

```bash
# 1) GitHub Issue 생성 → 이슈 번호 확보 (예: #12)
# 2) develop 최신화
git checkout develop
git fetch upstream && git merge upstream/develop
# 3) 이슈 번호를 포함한 feature 브랜치 생성 (규칙: docs/convention/branch-convention.md)
git checkout -b feature/<issue-number>-<short-description>
# 4) 작업 후 커밋(Conventional Commits) & 내 fork로 push
git add . && git commit -m "feat(scope): ..."
git push origin feature/<issue-number>-<short-description>
# 5) GitHub에서 PR 생성(본문에 Closes #이슈번호): <me>/<repo>:feature/...  →  <team>/<repo>:develop
```

머지는 리뷰 승인 + CI 통과 후 머지 권한자가 합니다. `main`/`develop`에는 직접 push하지 않습니다.

## 프로젝트가 정해진 뒤 먼저 할 일

1. [docs/project/project-brief.md](docs/project/project-brief.md) 작성
2. [docs/requirements/non-functional-requirements.md](docs/requirements/non-functional-requirements.md) 갱신
3. [docs/architecture/system-overview.md](docs/architecture/system-overview.md) 갱신 (기술 스택 표 포함)
4. [.github/workflows/ci.yml](.github/workflows/ci.yml)에 실제 빌드/테스트 단계 추가
5. 첫 ADR 작성 ([docs/adr/0000-adr-template.md](docs/adr/0000-adr-template.md) 복사)

## 핵심 디렉터리 한눈에 보기

```text
.github/     GitHub 협업 템플릿과 Actions(CI)
.claude/     Claude Code 권한·보안 설정
docs/        사람이 읽는 문서(프로젝트·요구사항·컨벤션·설계·ADR)
src/         실제 백엔드 코드 위치(main/test)
```

---

# 폴더·파일 설명

아래는 저장소에 포함된 모든 폴더와 파일이 **무엇이고 왜 존재하는지**에 대한 설명입니다.

> 📌 **`docs/` 하위 문서는 재사용 가능한 템플릿 상태입니다.** 프로젝트·도메인·스택이 확정되면 각 문서 상단 안내에 따라 `TBD`를 실제 값으로 채워 사용합니다. (구조·원칙·체크리스트는 그대로 두고 값만 채우면 됩니다)

## 1. 루트 메타/설정 파일

프로젝트의 초기 설정, 협업 규칙, 개발 환경 구성을 정의하는 최상위 메타데이터입니다.

| 파일                                | 역할                         | 설명                                                                                                                                       |
| ----------------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| [README.md](README.md)                 | 프로젝트 소개 및 시작 가이드 | 기술 스택·디렉터리 구조·초기 설정 방법과 이 폴더/파일 설명을 담는다. |
| [.gitignore](.gitignore)               | Git 제외 설정                | `CLAUDE.local.md`, `.env`, 시크릿(키·인증서), 빌드 산출물, 의존성, IDE/OS 파일 등을 커밋에서 제외한다.                                |
| [.editorconfig](.editorconfig)         | 에디터 스타일 표준화         | UTF-8 인코딩, LF 줄바꿈, 최종 줄바꿈 삽입, 공백 들여쓰기 등 IDE 간 일관성을 위한 설정이다.                                                 |
| [.mcp.example.json](.mcp.example.json) | MCP 서버 설정 예시           | Claude Code가 사용할 MCP 서버(filesystem, github)를 `npx`로 설치하고 토큰 환경변수를 주입하는 설정 예시다.                               |

## 2. `.github/` — GitHub 협업·CI 템플릿

PR/이슈 템플릿과 GitHub Actions 자동화를 정의합니다.

| 파일                                                                                | 역할                    | 설명                                                                                                                                  |
| ----------------------------------------------------------------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| [.github/CODEOWNERS](.github/CODEOWNERS)                                               | 코드 소유권/리뷰 담당자 | 기본 소유자와 `docs/`, `.claude/`, `.github/`별 소유자를 지정해 PR 리뷰 자동 할당의 근거를 제공한다.                            |
| [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md)                   | PR 제출 양식            | 변경 목적, 주요 변경 사항, 테스트/문서 갱신 여부, 리스크, Claude Code 검토를 포함하는 PR 체크리스트다.                                |
| [.github/ISSUE_TEMPLATE/config.yml](.github/ISSUE_TEMPLATE/config.yml)                 | 이슈 템플릿 설정        | `blank_issues_enabled: true`로 설정해 사전 정의 템플릿 외 자유 형식 이슈도 허용한다.                                                |
| [.github/ISSUE_TEMPLATE/feature_request.md](.github/ISSUE_TEMPLATE/feature_request.md) | 기능 요청 템플릿        | 배경·요구사항·완료 조건·테스트 조건·문서 갱신·참고 자료 구조의 기능 요청 양식이다.                                               |
| [.github/ISSUE_TEMPLATE/bug_report.md](.github/ISSUE_TEMPLATE/bug_report.md)           | 버그 보고 템플릿        | 동일 구조로 문제 상황을 기록하는 버그 보고 양식이다.                                                                                  |
| [.github/ISSUE_TEMPLATE/task.md](.github/ISSUE_TEMPLATE/task.md)                       | 일반 작업 템플릿        | 일반 업무 작업을 위한 이슈 양식이다.                                                                                                  |
| [.github/ISSUE_TEMPLATE/refactoring.md](.github/ISSUE_TEMPLATE/refactoring.md)         | 리팩토링 템플릿         | 코드 리팩토링 작업을 위한 이슈 양식이다.                                                                                              |
| [.github/ISSUE_TEMPLATE/documentation.md](.github/ISSUE_TEMPLATE/documentation.md)     | 문서 작업 템플릿        | 문서 작성·갱신을 위한 이슈 양식이다.                                                                                                 |
| [.github/workflows/ci.yml](.github/workflows/ci.yml)                                   | 기본 CI 파이프라인      | PR 및 `main`/`develop` 푸시 시 editorconfig 검사와 Gradle 빌드(`spotlessCheck build`: 포맷·컴파일·테스트)를 실행한다. |

## 3. `.claude/` — Claude Code 설정

Claude Code 에이전트의 권한과 보안 경계를 정의합니다.

| 파일                                                                    | 역할                 | 설명                                                                                                                                            |
| ----------------------------------------------------------------------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| [.claude/settings.json](.claude/settings.json)                             | 전역 보안·권한 설정 | 문서/소스 읽기와 git 조회·셸 실행은 허용하되,`.env`·시크릿·credentials 읽기와 파괴적 명령(`rm -rf`, `kubectl delete` 등)을 차단한다.   |
| [.claude/settings.local.example.json](.claude/settings.local.example.json) | 로컬 환경 설정 예시  | 환경을 `local`로 두고 `npm`/`gradle`/`pytest`/`go test` 실행 권한을 추가하는 예시. 개발자가 로컬에서 테스트를 직접 돌릴 수 있게 한다. |

## 4. `docs/` — 사람이 읽는 문서

> 아래 문서들은 **재사용 가능한 템플릿**입니다. 프로젝트 확정 후 각 문서의 `TBD`를 실제 값으로 채워 사용합니다.

| 파일                        | 역할                   | 설명                                                                           |
| --------------------------- | ---------------------- | ------------------------------------------------------------------------------ |
| [docs/index.md](docs/index.md) | 문서 네비게이션 엔트리 | 전체 문서의 목적·내용을 안내하는 인덱스. 하위 폴더별 문서를 표로 정리한 목차. |

### 4-1. `docs/project/` · `docs/requirements/` · `docs/adr/`

| 파일                                                                                              | 역할                 | 설명                                                                                                |
| ------------------------------------------------------------------------------------------------- | -------------------- | --------------------------------------------------------------------------------------------------- |
| [docs/project/project-brief.md](docs/project/project-brief.md)                                       | 프로젝트 개요        | 프로젝트의 목적·범위·주요 요구사항을 정리하는 템플릿. 확정 후 실제 프로젝트 내용으로 채운다.      |
| [docs/requirements/non-functional-requirements.md](docs/requirements/non-functional-requirements.md) | 비기능 요구사항      | 성능·보안·확장성·가용성 등 품질 요구사항 템플릿.                                                 |
| [docs/requirements/user-story-template.md](docs/requirements/user-story-template.md)                 | 사용자 스토리 템플릿 | 사용자 스토리를 일관 형식으로 작성하기 위한 템플릿.                                                 |
| [docs/adr/README.md](docs/adr/README.md)                                                             | ADR 저장소 안내      | 중요 기술 결정을 기록·추적하는 ADR 폴더의 목적·사용법 안내 템플릿.                                |
| [docs/adr/0000-adr-template.md](docs/adr/0000-adr-template.md)                                       | ADR 표준 템플릿      | 모든 ADR이 따를 구조(Status·Context·Decision·Alternatives·Consequences·Validation)를 정의한다. |

### 4-2. `docs/convention/` · `docs/api/`

| 파일                                                                                    | 역할             | 설명                                                                                                    |
| --------------------------------------------------------------------------------------- | ---------------- | ------------------------------------------------------------------------------------------------------- |
| [docs/convention/collaboration-convention.md](docs/convention/collaboration-convention.md) | 협업 컨벤션      | **Fork 기반 PR 워크플로우**(브랜치 전략·git 플로우·PR 작성/리뷰 규칙·GitHub Ruleset). 팀이 확정한 실제 협업 방식. |
| [docs/convention/branch-convention.md](docs/convention/branch-convention.md)               | 브랜치 컨벤션    | **이슈 기반 브랜치 네이밍**(`<type>/<이슈번호>-<설명>`)과 머지 전략. 팀이 확정한 실제 규칙. |
| [docs/convention/code-style.md](docs/convention/code-style.md)                             | 코드 스타일      | **Java/Spring 코드 스타일**(Gradle·Spotless, 네이밍, 모듈러 모놀리식+DDD 계층, DI). 팀이 확정한 실제 규칙. |
| [docs/convention/commit-convention.md](docs/convention/commit-convention.md)               | 커밋 컨벤션      | **Conventional Commits** 기반 커밋 메시지 규칙. 팀이 확정한 실제 규칙. |
| [docs/api/api-design-guide.md](docs/api/api-design-guide.md)                               | API 설계 가이드  | RESTful endpoint·요청/응답 구조 등 설계 원칙 템플릿.                                                   |
| [docs/api/error-response-guide.md](docs/api/error-response-guide.md)                       | 에러 응답 가이드 | 에러 응답 형식·코드·메시지 정의 방식 템플릿.                                                          |

### 4-3. `docs/architecture/`

| 파일                                                                      | 역할        | 설명                                                                                                                 |
| ------------------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------- |
| [docs/architecture/system-overview.md](docs/architecture/system-overview.md) | 시스템 개요 | 시스템 전체의 고수준 구조 템플릿(컨텍스트 다이어그램·컴포넌트 표·기술 스택 표). 확정 후 실제 시스템에 맞게 채운다. |

### 4-4. `docs/database/`

| 파일                                                                | 역할              | 설명                                         |
| ------------------------------------------------------------------- | ----------------- | -------------------------------------------- |
| [docs/database/database-design.md](docs/database/database-design.md)   | DB 설계           | 데이터베이스 구조·스키마·관계 설계 템플릿. |
| [docs/database/migration-policy.md](docs/database/migration-policy.md) | 마이그레이션 정책 | 스키마 변경·버전 관리·롤백 절차 템플릿.    |

## 5. `src/` — 실제 백엔드 코드 위치

| 경로 | 역할 | 설명 |
| --- | --- | --- |
| [src/main/java/com/kohere/](src/main/java/com/kohere/) | 메인 소스 | `KohereApplication` + 도메인 모듈 패키지(모듈러 모놀리식, [code-style](docs/convention/code-style.md) §3). |
| [src/test/java/com/kohere/](src/test/java/com/kohere/) | 테스트 소스 | 컨텍스트 로드 테스트와 모듈 경계 검증(`ModularityTest`). |

---

## 검증

CI([.github/workflows/ci.yml](.github/workflows/ci.yml))는 두 가지를 검사합니다.

- **editorconfig** — [.editorconfig](.editorconfig) 준수 여부(editorconfig-checker)
- **build** — `./gradlew spotlessCheck build` (포맷 검사 → 컴파일 → 테스트 → 모듈 경계 검증)

로컬에서는 커밋 전에 `./gradlew spotlessApply`로 포맷을 정렬하고 `./gradlew build`로 검증합니다 (JDK 21 필요).

---

## 로컬 실행 · 테스트

로컬은 `docker-compose`로 인프라(MySQL·MongoDB·Redis·MailHog·MinIO)를 띄우고, 앱은 `bootRun`으로 실행합니다. 배포 아키텍처는 [system-overview](docs/architecture/system-overview.md)를 참고하세요. (예시는 bash 기준 — PowerShell은 `./gradlew`를 `.\gradlew`로)

**사전 준비**: JDK 21, Docker Desktop

### 1. 인프라 기동

```bash
docker compose up -d mysql mongo redis mailhog minio minio-init
```

- **MailHog** = 온보딩 이메일 인증용 가짜 SMTP. 받은 메일은 <http://localhost:8025> 에서 확인합니다.
- **MinIO** = 매물 사진용 S3 호환 저장소([ADR-0041](docs/adr/0041-listing-image-upload-to-s3.md)). 올라간 객체는 <http://localhost:9001> 콘솔(`minioadmin` / `minioadmin`)에서 확인합니다. 버킷은 `minio-init`이 한 번 만들고 종료합니다 — MinIO 없이 다른 기능만 볼 때는 `APP_IMAGES_ENABLED=false`로 띄우면 업로드 없이 URL만 만드는 스텁이 붙습니다(그 URL은 열리지 않습니다).
- 마이그레이션 충돌 시(이미 적용된 `V*`를 수정한 경우) `docker compose down -v`로 볼륨을 초기화한 뒤 다시 띄웁니다.

### 2. 앱 실행

```bash
./gradlew bootRun
```

- 프로파일 기본값은 `local`, 기동 시 Flyway가 스키마를 적용합니다.
- 헬스 체크: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}` (앱+인프라 정상)

### 3. API 문서 (Swagger UI · REST Docs)

`bootRun`에서 보려면 생성물을 한 번 모읍니다(테스트 실행 → OpenAPI/HTML 생성):

```bash
./gradlew prepareDevStatic
```

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>

> 배포 이미지(`bootJar`)에는 자동 포함됩니다. `clean` 후에는 `prepareDevStatic`를 다시 실행하세요.

Swagger UI는 **소유 모듈 기준 7개 그룹**으로 나뉩니다(표시 순서 = [swagger-ui-initializer.js](src/main/resources/swagger-ui-initializer.js)의 `TAG_ORDER`, 상단 검색창으로 필터 가능):

| 태그 | 범위 | 스펙 |
| --- | --- | --- |
| `Auth` | 소셜 로그인 · 약관 · 온보딩 · 연락처/사업자 검증 · 토큰 재발급 | [01](docs/api/specs/01-auth-onboarding.md) |
| `Users` | 내 프로필 조회·수정·탈퇴 · 차단 목록 | [01](docs/api/specs/01-auth-onboarding.md) |
| `Diagnosis` | 5단계 맞춤 진단(v1 회원 · v2 게스트 허용)과 추천 | [02](docs/api/specs/02-diagnosis-recommendation.md) |
| `Bookings` | 매물 신청 생성·조회·삭제·차단·신고 | [04](docs/api/specs/04-booking-inquiry-chat.md) |
| `Quiz` | 학습 퀴즈 조회·채점 | [06](docs/api/specs/06-gamification.md) |
| `LifeTips` | 주제별 생활 팁 | [08](docs/api/specs/08-life-tips.md) |
| `Listings` | 매물 탐색·지도·검색·찜·최근 본 매물 | [03](docs/api/specs/03-listings-favorites.md) |

> 귀속 기준은 경로가 아니라 **소유 모듈**입니다 — `/users/me/favorites`는 listing 모듈 소유라 `Listings`에 있습니다.

문서 테스트 작성 규약과 생성기 한계는 [ADR-0017](docs/adr/0017-openapi-swagger-ui-from-restdocs.md) 「문서 작성 규약」을 따릅니다. `./gradlew build`가 `verifyOpenApiSpec`으로 태그·문구·operationId 규약을 검증합니다.

### 4. 인증·온보딩 수동 테스트 (`.http`)

[http/auth-onboarding.http](http/auth-onboarding.http)를 VS Code **REST Client** 확장으로 엽니다.

1. `@idToken`에 Google `id_token`을 넣습니다 — [OAuth Playground](https://developers.google.com/oauthplayground)에서 scope `openid email profile`로 Authorize → **Step 2 "Exchange authorization code for tokens"** 응답의 `id_token`(`eyJ...`)을 복사합니다. (Step 1의 인가 코드 `4/0...`가 **아닙니다**.)
   - 로컬은 `GOOGLE_CLIENT_ID` 미설정이라 audience 검증을 건너뛰므로, 유효한 Google 서명 토큰이면 통과합니다.
2. 위에서부터 순서대로 호출 → 3번(인증번호 발송) 후 <http://localhost:8025> 에서 코드를 확인해 `@code`에 입력 → 끝까지 진행합니다.

### 5. 빌드·테스트

```bash
./gradlew spotlessApply   # 포맷 정렬(커밋 전 필수)
./gradlew build           # 컴파일 + 테스트 + 모듈 경계 검증
```
