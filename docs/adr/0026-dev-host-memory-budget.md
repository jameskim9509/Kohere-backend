# ADR-0026. dev 단일 호스트(t3.small·2GB)의 메모리 예산을 스왑 + 엔진 캡으로 맞춘다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0026 |
| 작성자 | James Kim |
| 작성일 | 2026-06-22 |
| 관련 문서 | [ADR-0021](./0021-cost-optimization-profile.md)(dev 단일 EC2 compose), [ADR-0006](./0006-refresh-token-store-redis.md)(Redis refresh 토큰), [docker-compose.yml.tftpl](../../infra/terraform/modules/dev/host/docker-compose.yml.tftpl), [user_data.sh.tftpl](../../infra/terraform/modules/dev/host/user_data.sh.tftpl) |

## Status

Accepted

## Context

dev는 EC2 1대 위 `docker-compose`로 **app(Spring Boot/JVM) · mysql:8.0 · mongo:7 · redis:7 · caddy:2** 5개 컨테이너를 함께 띄운다([ADR-0021](./0021-cost-optimization-profile.md)). 비용을 더 줄이기 위해 인스턴스를 **t3.medium(4GB) → t3.small(2GB)** 로 낮췄다.

문제: compose에는 **컨테이너 메모리 제한이 없고**, user_data에도 **스왑이 없다**. 그러면 각 엔진이 *호스트 RAM 기준 기본값*으로 메모리를 잡는다 — 2GB에서 이 기본값들의 합이 가용 RAM을 넘겨 **부팅 동시 기동 시점부터 OOM-killer가 컨테이너를 죽이고 `restart: unless-stopped` 와 맞물려 재시작 루프**에 빠지기 쉽다.

캡을 적용하지 않은 2GB 기준 대략 예산(RSS 어림):

| 구성요소 | 기본 동작 | 대략 RSS |
|---|---|---|
| OS + docker daemon | — | ~250 MB |
| caddy | — | ~40 MB |
| redis | dev 사용량 미미 | ~30 MB |
| mysql 8.0 | `performance_schema` ON + 버퍼풀 128M | ~450 MB |
| mongo 7 | WiredTiger 캐시 = 50%×(RAM−1GB) ≈ **512 MB** | ~650 MB |
| app(JVM) | MaxHeap = RAM의 25% ≈ **512 MB** + 비힙 | ~750 MB |
| **합계** | | **~2170 MB** |

가용 ~1950 MB(커널 예약 후)를 **~220 MB 초과** → OOM. mongo와 JVM이 *호스트 2GB*를 기준으로 캐시/힙을 키우는 게 주범이다.

## Decision

**기본값을 호스트(2GB)가 아니라 dev 워크로드에 맞게 캡하고, 버스트는 스왑으로 흡수한다.** per-컨테이너 `mem_limit`은 두지 않는다(아래 근거).

- **엔진 베이스라인 캡** ([docker-compose.yml.tftpl](../../infra/terraform/modules/dev/host/docker-compose.yml.tftpl))
  - **mongo**: `command: ["--wiredTigerCacheSizeGB", "0.25"]` — 캐시를 512MB→256MB로 절반. 엔트리포인트가 `--auth`는 그대로 유지.
  - **mysql**: `command: ["--innodb-buffer-pool-size=128M", "--performance-schema=OFF"]` — `performance_schema` OFF가 핵심(수백 MB 절감), 버퍼풀은 명시.
  - **app(JVM)**: `JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m -XX:MaxMetaspaceSize=160m"` — 힙을 *명시*한다. mem_limit이 없으면 `MaxRAMPercentage`는 호스트 2GB를 기준으로 산정하므로 의미가 없어 **명시 `-Xmx`** 로 잡는다.
- **스왑 2GB** ([user_data.sh.tftpl](../../infra/terraform/modules/dev/host/user_data.sh.tftpl) step 0) — `/swapfile`(`vm.swappiness=10`). 정상 워크로드는 RAM에 두고, GC·쿼리·이미지 pull 같은 **일시적 스파이크만 스왑으로 흡수**해 하드 OOM을 막는다. 스왑 설정 실패가 부팅을 막지 않도록 best-effort(`set -e` 회피).

캡 적용 후 대략 예산:

| 구성요소 | 캡/설정 | 대략 RSS |
|---|---|---|
| OS + docker daemon | — | ~250 MB |
| caddy | — | ~40 MB |
| redis | — | ~30 MB |
| mysql 8.0 | 버퍼풀 128M + `performance_schema` OFF | ~300 MB |
| mongo 7 | WiredTiger 캐시 0.25 GB | ~400 MB |
| app(JVM) | `-Xmx512m` + Metaspace≤160m + 스레드/오프힙 | ~800 MB |
| **합계** | | **~1820 MB** |

가용 ~1950 MB 안에 **~130 MB 여유**로 들어가고, 스파이크는 스왑 2GB가 받는다.

**왜 per-컨테이너 `mem_limit`은 안 두나**: 2GB는 5개 한도의 합을 안전히 나눠 갖기엔 너무 빠듯하다. 한도를 낮게 잡으면 정상 부하에서도 cgroup OOM으로 *개별 컨테이너*가 반복 kill되는 역효과가 난다. 베이스라인 캡(위)으로 footprint를 줄이고 스왑으로 완충하는 편이 dev에서 더 안정적이다. 하드 격리가 필요해지면 후속으로 도입한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| t3.medium(4GB) 유지 | 캡·스왑 불필요, 여유 | 월 ~$15 더(2배) | dev 비용 최소화 목표([ADR-0021])에 배치 |
| 캡 없이 스왑만 | 변경 최소 | mongo/JVM이 여전히 호스트 2GB 기준 → 상시 스왑 스래싱·느림 | 베이스라인을 안 줄이면 스왑이 상시 경로가 됨 |
| per-컨테이너 `mem_limit` | 하드 격리 | 2GB에 5개 한도 배분이 빠듯 → 개별 OOM 루프 | 베이스라인 캡 + 스왑이 dev에 더 안정적 |
| redis `maxmemory`+eviction | redis 상한 | `allkeys-lru`는 **refresh 토큰 축출** 위험([ADR-0006]) | dev redis 사용량이 미미해 불필요, 토큰 손실 회피 |

## Consequences

- 긍정: t3.small(2GB)에서 5개 컨테이너가 **OOM 루프 없이** 기동. 인스턴스 비용 ~50%↓([ADR-0021] 갱신: ~$32→~$17/mo). 캡 값은 compose/user_data 주석으로 자기설명적.
- 부정/트레이드오프: 헤드룸이 얇아(**~130MB**) 부하·데이터가 커지면 스왑 의존도가 올라 **지연**이 생길 수 있다 → 그때는 t3.medium으로 복귀(인스턴스 타입만 변경). mongo 캐시 256MB·JVM 힙 512MB는 dev 트래픽 가정값이라 **prod엔 적용하지 않는다**(prod는 관리형 RDS/DocumentDB + ECS 태스크 사이징).
- 후속 작업: dev에서 OOM/스왑 스래싱이 관측되면 (a) t3.medium 승격 또는 (b) 컨테이너별 `mem_limit` 도입을 검토. CloudWatch 메모리 지표는 기본 미수집이라 필요 시 CloudWatch Agent 추가.

## Validation

- 부팅 후 SSM 접속 → `free -m`(스왑 2048MB 활성), `sudo docker compose ps`(app/mysql/mongo/redis/caddy 모두 `Up`, 재시작 루프 없음).
- `sudo docker stats --no-stream` 으로 각 컨테이너 RSS가 위 예산 어림과 부합하는지.
- `sudo cat /var/log/devhost-init.log` 에 스왑 설정·compose up 정상. mongo는 `--auth` 유지되어 인증 접속 성공.
- 부하 후 `vmstat`/`free` 로 스왑이 상시 가득(스래싱)이 아니라 스파이크 시에만 쓰이는지 확인.
