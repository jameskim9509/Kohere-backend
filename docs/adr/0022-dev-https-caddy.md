# ADR-0022. dev HTTPS 종단은 Caddy로 한다 (nginx + certbot 대비)

| 항목 | 값 |
|---|---|
| 번호 | ADR-0022 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-06-21 |
| 관련 문서 | [ADR-0021](./0021-cost-optimization-profile.md), [infra/terraform](../../infra/terraform/README.md) |

## Status

Accepted

> [ADR-0021](./0021-cost-optimization-profile.md)의 dev 구성(ALB 없이 EC2 1대 `docker-compose`)에서 **HTTPS(443) 종단을 무엇으로 할지** 정한다. prod은 ALB가 ACM 인증서로 443을 종단하지만, dev엔 ALB가 없다.

## Context

- dev는 EC2 1대에 컨테이너로 앱을 올리고 EIP/Route53으로 노출한다([ADR-0021](./0021-cost-optimization-profile.md)). 외부에 **HTTPS**를 제공하려면 compose 안에서 **TLS 종단 + Let's Encrypt 인증서 자동 발급·갱신**이 필요하다.
- 후보는 **nginx + certbot**(전통적 조합)과 **Caddy**(자동 HTTPS 내장)다.
- **핵심 운영 포인트는 "인증서 갱신 후 적용(reload)"의 자동화**다:
  - **nginx + certbot**: certbot이 90일마다 인증서를 갱신하면 **nginx가 새 인증서를 읽도록 reload**해야 한다. compose 환경에서 nginx reload는 **호스트에서 `docker exec nginx nginx -s reload`(또는 `docker kill -s HUP`) 같은 docker 명령을 실행**해야 한다 — certbot 컨테이너가 nginx 컨테이너를 reload하려면 **docker 소켓 마운트나 호스트 스크립트(cron/deploy-hook)** 가 필요하다. 즉 **호스트-측 docker 명령 의존**이 생겨 자동화·권한 관리가 번거롭다.
  - **Caddy**: 인증서 **발급·갱신·적용을 자체 프로세스 안에서 자동** 처리한다. 외부 certbot도, 호스트 docker 명령도 필요 없다.

## Decision

**dev의 HTTPS 종단은 Caddy로 한다.** `docker-compose`에 `caddy:2` 컨테이너 하나를 두고, Caddyfile 한 블록으로 자동 HTTPS + 리버스 프록시를 구성한다.

```caddyfile
dev.example.com {
  reverse_proxy app:8080
}
```

- Caddy가 `dev.example.com`에 대해 **Let's Encrypt 인증서를 자동 발급하고 만료 전 자동 갱신**하며, **갱신 후 reload도 내부에서 무중단 처리**한다(호스트 docker 명령 불필요).
- 도메인이 아직 없으면 사이트 주소를 `:80`으로 폴백해 HTTP로 서빙한다(인증서 시도 없음). 도메인 확정 시 한 줄 교체로 HTTPS가 켜진다.
- 인증서·설정은 named volume(`caddy_data`/`caddy_config`)에 영속한다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **Caddy (채택)** | 자동 HTTPS 내장(발급·갱신·reload 자체 처리), 설정 한 줄, 운영 단순 | nginx보다 생태계·세밀 튜닝 사례 적음 | — |
| **nginx + certbot** | 익숙·세밀 제어 | **갱신 후 nginx reload에 호스트 docker 명령 필요** → docker 소켓/호스트 스크립트로 자동화해야 해 복잡, 권한 표면↑ | dev에 과한 운영 부담 |
| **nginx-proxy + acme-companion** | compose 라벨로 자동화 | nginx-proxy에 **docker 소켓 마운트** 필요(컨테이너가 docker API 접근 = 권한 표면↑), 컴포넌트 2개 | Caddy 한 컨테이너가 더 단순·안전 |
| **ALB + ACM (prod 방식)** | 매니지드 종단·자동 갱신 | ALB 비용·복잡도 | dev엔 과투자([ADR-0021](./0021-cost-optimization-profile.md)) |

## Consequences

- **긍정**
  - **인증서 자동화 단순** — 발급·갱신·reload를 Caddy가 자체 처리. 호스트 docker 명령·cron·deploy-hook·docker 소켓 마운트가 **모두 불필요**.
  - Caddyfile 한 블록이면 끝 — dev 운영 부담 최소.
  - 도메인 미설정 시 `:80` 폴백으로 깨지지 않는다.
- **부정/트레이드오프**
  - nginx 대비 **생태계가 작아** 매우 세밀한 프록시 튜닝이 필요할 땐 레퍼런스가 적다(dev 용도엔 충분).
  - prod(ALB)과 **종단 방식이 다르다** — 단 dev 한정이고 앱 계약은 동일.
  - 도메인이 **자기 소유**여야 Let's Encrypt 발급이 된다(example 도메인은 발급 실패, 실 도메인 교체 시 동작).
- **후속 작업**
  - 실 dev 도메인 확정 시 `domain_name`(+`route53_zone_id`)을 채워 HTTPS 활성화.

## Validation

- `domain_name`을 실제 소유 도메인으로 설정 후 apply → `https://<domain>`이 유효 인증서로 응답하는지 확인.
- 갱신 시점(또는 강제 갱신)에 **호스트 개입 없이** 인증서가 교체되는지 확인.
- 도메인 미설정(`:80`) 시 HTTP로 정상 서빙되는지 확인.
