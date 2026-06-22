# ADR-0024. 변경된 시크릿은 배포(CI/CD)에서 재조회·워크로드 재생성으로 반영한다(런타임 핫 리로드·apply 트리거 미도입)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0024 |
| 작성자 | James Kim |
| 작성일 | 2026-06-22 |
| 관련 문서 | [ADR-0023](./0023-secrets-in-ssm-parameter-store.md)(시크릿=SSM Parameter Store), [ADR-0021](./0021-cost-optimization-profile.md)(dev 단일 EC2 compose), [ADR-0006](./0006-refresh-token-store-redis.md)(Redis 인메모리), [.github/workflows/deploy.yml](../../.github/workflows/deploy.yml), [modules/dev-host](../../infra/terraform/modules/dev-host) |

## Status

Accepted

## Context

시크릿은 SSM Parameter Store SecureString에 둔다([ADR-0023](./0023-secrets-in-ssm-parameter-store.md)). 그런데 "저장"과 별개로, **저장된 값이 바뀌었을 때 실행 중 워크로드에 어떻게 반영하는가**는 환경마다 메커니즘이 다르다.

- **dev**([ADR-0021](./0021-cost-optimization-profile.md)): EC2 1대 docker-compose. 앱 컨테이너는 `env_file: /opt/kohere/.env`로 시크릿을 읽고, 그 `.env`는 **부팅 시 1회** 인스턴스 프로파일로 SSM에서 받아 기록한다. `env_file`은 **컨테이너 생성 시점에만** 읽히므로, 새 값이 먹으려면 ① `.env` 파일이 갱신되고 ② 앱 컨테이너가 **recreate** 돼야 한다. 단순 `compose up`(변경 없음)이나 재부팅(cloud-init은 user_data를 인스턴스당 1회 실행)으로는 갱신되지 않는다.
- **prod**: ECS Fargate. task definition의 `secrets`(valueFrom = SSM 파라미터 ARN)로 **태스크가 뜰 때마다** task execution role이 최신값을 주입한다. 부팅 1회 `.env` 같은 staleness는 없지만, **이미 실행 중인 태스크**는 시작 시점 값을 유지한다.
- 공통 난점: **SSM 값만 바꿔도 실행 중 워크로드는 옛 값**을 쓴다. 명시적 재조회/재시작이 필요하다.
- 제약: SSM 파라미터는 Terraform이 소유(desired state)한다. Terraform은 데몬이 아니라 **`apply` 시점에만** 동작한다. 또한 SSM `SendCommand`는 **보내는 쪽(sender)** 권한이며, EC2/ECS는 받는 쪽이다(EC2 = `AmazonSSMManagedInstanceCore` 수신, 배포 sender = GitHub OIDC 역할).

## Decision

**시크릿 반영은 "명시적 재조회 + 워크로드 재생성"으로 하고, 그 트리거는 배포(CI/CD)로 단일화한다.** 앱 런타임 핫 리로드도, `terraform apply` 시점의 자동 트리거도 두지 않는다. prod·dev가 **"배포가 반영 경로"** 라는 같은 모델을 공유한다.

### 공통 모델

- **Terraform/SSM = desired state**(시크릿 값이 무엇이어야 하는가) / **CI/CD = 런타임 반영**(그 값을 실행 중 워크로드에 굴리는 것). 둘을 분리한다.
- 시크릿 값을 바꾸려면: tfvars 수정(또는 random 회전) → `apply`로 SSM 갱신 → **배포**로 워크로드에 반영.

### dev — refresh 스크립트 + 배포 트리거

1. **재조회 스크립트 분리**: SSM→`.env` 조회 로직을 [`refresh-env.sh`](../../infra/terraform/modules/dev-host/refresh-env.sh.tftpl)로 분리해 **부팅·배포가 공용**으로 호출한다. (dev는 `.env`가 부팅 1회라 prod와 달리 이 스크립트가 꼭 필요하다.)
2. **배포 경로(GitHub Actions)** — [deploy.yml](../../.github/workflows/deploy.yml): `main` push **또는 `workflow_dispatch`** → 이미지 빌드·ECR push(`:dev`) → `aws ssm send-command`(AWS-RunShellScript)로 대상 EC2에서 `refresh-env.sh` → `docker compose pull app` → `docker compose up -d --force-recreate app`. sender = GitHub OIDC 배포 역할(인스턴스 한정 `ssm:SendCommand`).
3. **app 컨테이너만 recreate** — `.env`(env_file)를 읽는 건 app뿐이므로 mysql/mongo/redis(인메모리, [ADR-0006](./0006-refresh-token-store-redis.md))는 유지해 다운타임·Redis 데이터 손실을 피한다.
4. **코드 변경 없이 시크릿만 바꾼 경우**: `apply`로 SSM을 갱신한 뒤 배포 워크플로를 수동 트리거(`gh workflow run deploy.yml`)해 반영한다. 별도 apply-time 자동 트리거는 두지 않는다(아래 대안 참조).

### prod — 배포(태스크 롤)가 반영 경로

prod(ECS)에는 별도 refresh 스크립트/트리거를 두지 않는다. ECS는 **태스크 시작 시** SSM에서 시크릿을 주입하므로, **시크릿 변경의 반영 = 배포(새 태스크 롤, `force-new-deployment`)** 다. 운영 배포 파이프라인이 새 task def 등록 + 태스크 롤을 수행하면 새 태스크가 최신값을 가져온다.

### 설계 의도·한계 (명시)

- **반영은 배포 시점이다.** Terraform/SSM은 desired state만 관리하고, 실행 중 워크로드 반영은 CI/CD가 담당한다(관측 가능한 로그·정상 sender 역할로).
- **트리거 기준은 Terraform desired 값**(tfvars/random)이며, 콘솔/CLI로 SSM을 직접 바꾼 **out-of-band 변경은 정본이 아니다** — 다음 `apply`에서 config 값으로 되돌린다. "출처 무관·배포 없이 자동"이 필요하면 EventBridge 경로(아래)로 전환해야 한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| dev `apply` 시점 자동 트리거(`terraform_data` + `local-exec`로 SSM SendCommand) | 시크릿만 바꿔 `apply`해도 자동 반영 | provisioner 안티패턴, operator 환경에 `awscli`+`bash`+`SendCommand` 의존(이식성), `on_failure=continue`로 **실패가 조용히 묻힘**, apply 출력에 묻혀 관측성↓, prod와 비대칭 | 가장 약한 고리. 배포(`workflow_dispatch`)가 동일 결과를 로그·정상 sender로 제공하므로 제거 |
| 앱 런타임 핫 리로드(SSM 폴링·Spring Cloud Config) | 재시작 불필요 | 앱 코드·의존성·운영 복잡도 증가 | MVP 과투자, 앱 변경 필요 |
| 인스턴스 교체(`user_data_replace_on_change=true` + 시크릿 해시) | Terraform 네이티브, 로컬 셸 불요 | EC2 교체 = 다운타임·Redis 인메모리 손실·EBS 재attach | 시크릿 변경마다 인스턴스 교체는 과함 |
| EventBridge(Parameter Store Change) → SSM Run Command | 출처 무관·배포 없이 자동 반영, sender=서비스 역할 | 규칙·Automation·역할 등 리소스 추가 | dev엔 과투자(배포 트리거로 충분). 완전 자동이 필요해지면 후속 도입 |
| prod `apply` 기반 `force-new-deployment` 트리거 | dev와 대칭(만약 dev가 apply 트리거였다면) | operator 자격증명 의존, prod 미배포라 효용 낮음 | 배포가 이미 태스크 롤로 반영하므로 불요 |

## Consequences

- 긍정: prod·dev가 **"배포가 반영 경로"** 로 대칭 → 멘탈 모델·문서 단순. 앱 코드 무변경, 추가 비용 0. 반영이 **배포 로그로 관측 가능**하고 sender가 정상 OIDC 역할이라 **operator 셸/자격증명 의존이 없다**. 프로비저닝(Terraform)과 배포(CI/CD)의 책임이 분리된다.
- 부정/트레이드오프: 코드 변경 없이 **시크릿만** 바꾼 경우에도 반영하려면 배포(`workflow_dispatch`) 한 스텝이 필요하다(자동 아님). **out-of-band 변경 미감지**(의도). dev는 `.env`가 부팅 1회라 crash-restart 시 디스크의 마지막 `.env`를 다시 읽을 뿐 SSM을 재조회하지 않는다(반영은 다음 배포에서).
- 후속 작업: prod 운영 배포 파이프라인 도입 시 "배포=시크릿 반영" 경로를 동일하게 적용·문서화. 자동(이벤트 기반)이 필요해지면 EventBridge → SSM Run Command로 전환(이 ADR을 Superseded/보완).

## Validation

- dev 배포 경로: 시크릿 변경 후 `main` push 또는 `gh workflow run deploy.yml` → SSM CommandId 실행 결과 성공, 대상 EC2에서 `docker exec kohere-app env | grep <KEY>`로 새 값 주입 확인(앱 컨테이너 재생성 시각 확인).
- desired state 정합: tfvars로 SSM을 관리하고, 콘솔 직접 변경은 다음 `apply`에서 되돌려지는지 확인(정본은 Terraform).
- prod: 시크릿 변경 후 배포(태스크 롤) 시 새 태스크가 최신 SSM 값을 주입받는지 확인(운영 배포 도입 시점).
- 한계 인지: out-of-band 변경 미반영·"시크릿-only 변경은 배포 필요"를 운영 가이드에 명시.
