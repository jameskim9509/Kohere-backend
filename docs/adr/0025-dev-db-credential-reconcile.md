# ADR-0025. dev 자가호스팅 DB 자격증명은 데이터 보존하며 reconcile로 회전한다(마커 기반)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0025 |
| 작성자 | James Kim |
| 작성일 | 2026-06-22 |
| 관련 문서 | [ADR-0021](./0021-cost-optimization-profile.md)(dev 단일 EC2 compose), [ADR-0023](./0023-secrets-in-ssm-parameter-store.md)(시크릿=SSM), [ADR-0024](./0024-secret-change-propagation.md)(시크릿 반영=배포), [modules/dev-host/reconcile-db.sh.tftpl](../../infra/terraform/modules/dev-host/reconcile-db.sh.tftpl) |

## Status

Accepted

## Context

dev는 자가호스팅 `mysql:8.0`·`mongo:7` 컨테이너를 EC2 1대에 올린다([ADR-0021](./0021-cost-optimization-profile.md)). DB 자격증명은 tfvars로 받아 비번은 SSM SecureString→`.env`, username은 compose 변수로 주입한다. 외부 접속(`db_ingress_cidrs`)도 열 수 있다.

문제: 컨테이너 이미지의 init 환경변수(`MYSQL_USER`/`MYSQL_PASSWORD`·`MONGO_INITDB_ROOT_*`)는 **빈 데이터 디렉터리에서 최초 init할 때만** 계정을 만든다. 자격증명은 **데이터 안**(`mysql.user` / mongo `admin.system.users`)에 저장돼 EBS(`/data`)에 영속하므로, **이미 데이터가 있으면 env를 바꿔도 무시**된다. 즉 데이터를 보존한 채 username·비번을 바꾸려면 **살아있는 DB에 admin 명령**(`ALTER USER`/`updateUser`)을 실행해야 한다.

추가 제약: admin/root 비번 자체를 바꾸려면 **옛 비번으로 인증**해야 하는 닭-달걀이 있다(앱 계정 비번은 root로 바꾸면 되지만, root/admin 비번 회전은 직전 값을 알아야 한다).

## Decision

**`reconcile-db.sh`로 데이터를 보존하며 자격증명을 `.env` 값으로 회전한다.** 직전 적용값을 마커에 저장해 닭-달걀을 푼다.

- **마커**: 직전 적용된 자격증명을 `/data/.db-state-mysql`·`/data/.db-state-mongo`(EBS·`chmod 600`)에 저장한다. reconcile은 **마커의 옛 자격증명으로 인증**해 `.env`의 새 값으로 회전한다. 마커가 **EBS(데이터와 같은 볼륨)에 있어** 인스턴스 교체에도 데이터와 함께 생존하고, **per-DB로 분리**해 한쪽 실패가 다른 쪽 인증을 막는 데드락을 피한다.
- **최초(마커 없음)**: DB가 방금 init env로 만들어졌으므로 desired=actual로 가정 → 인증 성공 → 사실상 no-op + 마커 시드.
- **회전 동작**: MySQL은 root로 인증해 앱 계정 `CREATE IF NOT EXISTS`+`ALTER`+`GRANT`, 이어 `ALTER USER 'root'@...`로 root 비번까지 회전. Mongo는 옛 자격증명으로 인증해 `updateUser`(비번)/`createUser`+`dropUser`(username 변경). **app 계정·root/admin 모두 회전 가능**.
- **호출 시점**: **부팅**(`user_data` — compose up 후, 마커 시드·best-effort) + **배포**(`deploy.yml` — `refresh-env` → `reconcile-db` → app `--force-recreate` 순). 배포가 먼저 DB를 새 자격증명으로 맞춘 뒤 앱을 새 `.env`로 재기동하므로 정합이 맞는다([ADR-0024](./0024-secret-change-propagation.md) 흐름에 reconcile 1스텝 추가).
- **prod 무관**: prod는 관리형 RDS/DocumentDB라 이 스크립트가 없다(자격증명은 SSM→ECS `secrets`, 콘솔/IAM로 관리).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| init-only(현행) + 변경 시 `/data` 초기화 | 단순 | **데이터 손실** | 운영 데이터 보존 불가 |
| Mongo admin(고정)/app(회전) 계정 분리 | 닭-달걀 없음 | 새 변수·compose 구조변경·app URI 변경 | 마커 방식으로 동일 효과를 더 적은 변경으로 달성 |
| 비번 마커 없이 root 고정만 회전 | 마커 불요 | root/admin 비번 회전 불가 | "username·비번 모두 회전" 요구 미충족 |
| Vault 등 동적 자격증명 | 강력·단명 | 인프라·운영 복잡 | dev 과투자([ADR-0021] 비용 최소화에 배치) |

## Consequences

- 긍정: **데이터 보존**하며 username·비번(앱·root/admin) 회전. 추가 비용 0. 부팅·배포가 같은 스크립트로 일관. 마커가 EBS에 있어 인스턴스 교체에도 견고.
- 부정/트레이드오프: 비번이 **SQL/JS 리터럴로 삽입**돼 따옴표·`@`·`$` 등은 깨질 수 있어 **셸/URI-safe 값 권장**. 마커에 **평문 비번 저장**(EBS 암호화 + `600`로 완화, `.env`와 동급 노출). 반영은 **배포/부팅 시점**에만(상시 아님). "인스턴스 교체와 비번 변경이 같은 apply에 겹치는" 드문 케이스는 수동 보정 필요.
- 후속 작업: 비번에 특수문자 허용이 필요해지면 SQL/JS 파라미터 바인딩(파일 주입)으로 강화. prod는 [ADR-0024] 배포(태스크 롤) 경로 유지.

## Validation

- 비번 회전: tfvars `mysql_password`/`mongo_password` 변경 → 배포 → reconcile 로그 `reconcile: mysql ok`/`mongo ok`, 새 비번으로 접속 성공·옛 비번 실패 확인. 데이터 잔존 확인.
- username 회전: `mysql_username`/`mongo_username` 변경 → 배포 → 새 계정 접속 OK, 옛 계정 `DROP`/`dropUser` 확인.
- 마커: `/data/.db-state-mysql`·`.db-state-mongo`가 새 값으로 갱신(600)됐는지.
- 부팅 시드: 신규 인스턴스 최초 부팅 후 마커 생성 + 앱 정상 접속.
