# Deployment Guide

> **예시 스택 안내**: 이 문서의 명령어/설정 예시는 **Spring Boot 3.x / Java 17 / Gradle / PostgreSQL / Flyway** 기준입니다.
> 실제 프로젝트의 스택이 확정되면 빌드 명령, 이미지 베이스, 환경변수 키 등을 프로젝트 값으로 교체하세요.
> 기술 스택이 미정인 경우 [tech-stack 규칙](../../.claude/rules/tech-stack.md)을 먼저 확정합니다.

## 목적

애플리케이션을 **빌드 → 컨테이너 이미지 생성 → 환경별 배포**하는 표준 절차와,
배포 전략(롤링/블루그린)·환경 구분(dev/staging/prod)·환경변수·헬스체크 기준을 정의한다.

배포 직전 점검은 반드시 [deployment-check 스킬](../../.claude/skills/deployment-check/SKILL.md)을 통해 수행한다.
문제가 발생하면 [rollback-guide](./rollback-guide.md)와 [incident-response](./incident-response.md)를 따른다.

---

## 1. 배포 파이프라인 개요

```text
  ┌──────────┐   ┌──────────┐   ┌──────────────┐   ┌────────────┐   ┌──────────┐
  │  commit  │──▶│  CI test │──▶│ build (gradle│──▶│ docker image│──▶│  deploy  │
  │  / PR    │   │  + lint  │   │ bootJar)     │   │  + push      │   │ (env별)  │
  └──────────┘   └──────────┘   └──────────────┘   └────────────┘   └──────────┘
                      │                                                    │
                      ▼                                                    ▼
               deployment-check                                      health check
               (사전 점검 스킬)                                       → 트래픽 전환
```

배포 결정 게이트:

| 게이트 | 판정 | 기준 |
| --- | --- | --- |
| CI 테스트 | Pass 필수 | 단위/통합 테스트, 정적분석 통과 |
| deployment-check | 배포 가능 / 조건부 / 보류 | migration·API 호환성·환경변수·롤백 가능성 점검 |
| staging 검증 | 수동 승인 | 스모크 테스트 + 핵심 지표 정상 |
| prod 배포 | 승인자 1인 이상 | 변경 윈도우/롤백 계획 확인 |

---

## 2. 빌드 (Gradle)

> 예시(Spring Boot 기준). Maven 프로젝트라면 `./mvnw clean package`로 교체한다.

```bash
# 1) 클린 빌드 + 테스트
./gradlew clean build

# 2) 테스트 제외하고 실행 가능한 jar만 (CI에서 테스트를 이미 돌린 경우)
./gradlew bootJar -x test

# 산출물 경로 (예시)
ls -al build/libs/
# app-0.1.0.jar   <- Spring Boot fat jar
```

빌드 산출물 규칙:

- 버전은 Git 태그 또는 커밋 SHA로 식별한다(예: `app-0.1.0+abc1234.jar`).
- 동일 커밋 → 동일 이미지 태그가 되도록 **재현 가능한 빌드**를 지향한다.
- `SNAPSHOT` 이미지는 prod에 배포하지 않는다.

---

## 3. 컨테이너 이미지

### 3.1 Dockerfile 예시 (멀티스테이지)

> 예시(Spring Boot / Java 17 기준). JRE 베이스 이미지와 버전은 프로젝트 정책에 맞춘다.

```dockerfile
# --- build stage ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew bootJar -x test

# --- runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
# non-root 사용자로 실행
RUN useradd -r -u 1001 appuser
COPY --from=build /workspace/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
```

### 3.2 빌드 & 푸시

```bash
# 이미지 태그 = 커밋 SHA (불변 태그 권장)
IMAGE="registry.example.com/team/app"
SHA="$(git rev-parse --short HEAD)"

docker build -t "${IMAGE}:${SHA}" -t "${IMAGE}:latest" .
docker push "${IMAGE}:${SHA}"
# 'latest'는 dev 환경 편의용. prod는 항상 불변 SHA 태그를 명시한다.
```

이미지 규칙:

- prod는 `latest`가 아니라 **불변 태그**(SHA 또는 릴리스 버전)로 배포한다.
- 이미지 취약점 스캔을 CI에 포함한다(예: 컨테이너 스캐너).
- 베이스 이미지는 정기적으로 패치 버전을 갱신한다.

---

## 4. 환경 구분

| 환경 | 용도 | 데이터 | 배포 방식 | 승인 |
| --- | --- | --- | --- | --- |
| `dev` | 개발/통합 확인 | 가짜/임시 데이터 | 자동(머지 시) | 불필요 |
| `staging` | prod 유사 검증 | 마스킹된 유사 데이터 | 자동 + 수동 승인 | 1인 |
| `prod` | 실제 사용자 | 운영 데이터 | 수동 트리거 | 1인 이상 + 변경 윈도우 |

원칙:

- staging은 가능한 한 prod와 **동일한 구성**(인스턴스 수, 리소스, 외부 연동 모드)을 유지한다.
- 환경별 차이는 **설정과 환경변수로만** 분리하고, 코드 분기로 하드코딩하지 않는다.
- Spring profile 예시: `--spring.profiles.active=prod` (값은 환경별로 주입).

---

## 5. 환경변수 / 설정

> 예시 키 이름입니다. **실제 값은 절대 코드/문서에 쓰지 않고** 시크릿 매니저나 배포 시크릿으로 주입합니다.
> 시크릿 취급 원칙은 [security 규칙](../../.claude/rules/security.md)을 따른다.

| 키 | 예시 값 | 시크릿 | 설명 |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` | 아니오 | 활성 프로파일 |
| `SERVER_PORT` | `8080` | 아니오 | 애플리케이션 포트 |
| `DB_URL` | `jdbc:postgresql://db.internal.example.com:5432/appdb` | 아니오 | DB 접속 URL |
| `DB_USERNAME` | `app_user` | 아니오 | DB 사용자 |
| `DB_PASSWORD` | `<INJECTED_BY_SECRET_MANAGER>` | **예** | DB 비밀번호 |
| `JWT_SECRET` | `<INJECTED_BY_SECRET_MANAGER>` | **예** | JWT 서명 키 |
| `FLYWAY_ENABLED` | `true` | 아니오 | 마이그레이션 자동 실행 여부 |
| `LOG_LEVEL` | `INFO` | 아니오 | 루트 로그 레벨 |

규칙:

- 시크릿은 환경변수 평문 출력 금지(로그/CI 로그 마스킹).
- 새 환경변수를 추가하면 이 표와 [deployment-check](../../.claude/skills/deployment-check/SKILL.md) 항목을 함께 갱신한다.
- 기본값이 없는 필수 키는 부팅 시 검증(없으면 빠르게 실패)한다.

---

## 6. 데이터베이스 마이그레이션 (Flyway)

> 예시(Flyway 기준). Liquibase 등 다른 도구를 쓰면 절차를 교체한다.

- 마이그레이션은 **expand-contract**(점진 확장→축소)로 작성해 무중단 배포를 가능하게 한다. 상세는 [rollback-guide](./rollback-guide.md#3-db-마이그레이션-롤백) 참고.
- 애플리케이션 시작 시 Flyway가 `db/migration/V*.sql`을 순서대로 적용한다.
- 운영에서 위험한 마이그레이션(대형 테이블 락, NOT NULL 추가 등)은 별도 윈도우/온라인 DDL로 분리한다. [database 규칙](../../.claude/rules/database.md) 준수.

```bash
# 적용 예정 마이그레이션 확인 (배포 전)
./gradlew flywayInfo

# 수동 적용이 필요한 경우 (자동 적용을 끈 환경)
./gradlew flywayMigrate
```

---

## 7. 배포 전략

### 7.1 롤링 배포 (기본)

인스턴스를 일부씩 교체하며 항상 일정 비율의 구버전을 유지한다.

```text
시작:  [v1][v1][v1][v1]
1단계: [v2][v1][v1][v1]   <- 1대 교체 후 헬스체크 통과 확인
2단계: [v2][v2][v1][v1]
완료:  [v2][v2][v2][v2]
```

- 장점: 추가 리소스 적음, 점진적 검증.
- 주의: v1/v2가 동시에 떠 있으므로 **DB/계약 호환성**(expand-contract)이 필수다.

### 7.2 블루그린 배포

신버전(green)을 완전히 띄운 뒤 트래픽을 한 번에 전환한다.

```text
  [router] ── 100% ─▶ blue(v1)        ← 현재 운영
                green(v2)  (대기/검증)

  검증 통과 후 전환:
  [router] ── 100% ─▶ green(v2)        ← 신규 운영
                blue(v1)   (즉시 롤백용으로 유지)
```

- 장점: 빠른 전환, 즉시 롤백(트래픽만 blue로 복귀).
- 주의: 두 환경 리소스가 동시에 필요, DB는 공유되므로 마이그레이션 호환성은 동일하게 중요.

| 항목 | 롤링 | 블루그린 |
| --- | --- | --- |
| 추가 리소스 | 적음 | 많음(2배) |
| 롤백 속도 | 보통(재배포) | 매우 빠름(트래픽 전환) |
| 호환성 요구 | 높음 | 높음 |
| 적합 상황 | 일반 변경 | 위험 변경/빠른 롤백 필요 |

---

## 8. 헬스체크

> 예시(Spring Boot Actuator 기준). 엔드포인트 경로는 프로젝트 설정에 맞춘다.
> 지표/관측은 [monitoring-metrics](./monitoring-metrics.md), [architecture/observability](../architecture/observability.md) 참고.

| 프로브 | 엔드포인트(예시) | 용도 | 실패 시 동작 |
| --- | --- | --- | --- |
| Liveness | `GET /actuator/health/liveness` | 프로세스 생존 | 컨테이너 재시작 |
| Readiness | `GET /actuator/health/readiness` | 트래픽 수용 가능 여부 | 라우터에서 제외(트래픽 차단) |
| Startup | `GET /actuator/health` | 부팅 완료 확인 | 부팅 대기 |

정상 응답 예시:

```json
{ "status": "UP", "components": { "db": { "status": "UP" }, "diskSpace": { "status": "UP" } } }
```

배포 게이트 규칙:

- 신규 인스턴스가 readiness `UP`을 반환하기 전에는 트래픽을 보내지 않는다.
- 헬스체크 실패가 임계치(예: 60초) 이상 지속되면 배포를 중단하고 자동 롤백한다.

---

## 9. 배포 절차 체크리스트

배포 전:

- [ ] [deployment-check 스킬](../../.claude/skills/deployment-check/SKILL.md) 실행 → 판정 "배포 가능"
- [ ] CI 테스트/정적분석 통과
- [ ] 마이그레이션이 expand-contract로 안전한지 확인(`flywayInfo`)
- [ ] 새/변경 환경변수가 대상 환경에 주입되어 있는지 확인(§5 표 갱신)
- [ ] 롤백 계획 확인([rollback-guide](./rollback-guide.md))
- [ ] (prod) 변경 윈도우/승인자 확보

배포 중:

- [ ] 신버전 헬스체크(readiness `UP`) 확인 후 트래픽 전환
- [ ] 골든 시그널(에러율/지연/포화) 실시간 관찰([monitoring-metrics](./monitoring-metrics.md))

배포 후:

- [ ] 스모크 테스트(핵심 시나리오) 통과
- [ ] 에러율/지연 정상 범위 복귀 확인(최소 15~30분 관찰)
- [ ] 구버전/blue 환경 정리 또는 롤백 대비 유지
- [ ] 배포 기록 남기기(버전, 시각, 담당자)
