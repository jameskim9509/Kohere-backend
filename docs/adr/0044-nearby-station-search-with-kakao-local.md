# ADR-0044. 인근 역은 카카오 로컬로 검색한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0044 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-15 |
| 기준 코드 | `feature/224-nearby-station-search-api` @ `1e50052`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0037](./0037-listing-localization-and-code-catalog.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [ADR-0041](./0041-listing-image-upload-to-s3.md), [ADR-0042](./0042-road-address-search-with-ncp-geocoding.md), [ADR-0043](./0043-remove-seeded-poi-keyword-search.md), [listing API](../api/specs/03-listings-favorites.md) |

## Status

Proposed

## Context

매물 등록 폼의 인근 역 칸에 두 가지 문제가 있다.

- **역 이름이 자유 입력이다.** `nearestTransit.name`은 임대인이 친 문자열을 그대로 저장하며 어떤 사전과도 대조하지 않는다([ListingRegisterService](../../src/main/java/com/kohere/listing/application/ListingRegisterService.java)). 오타가 그대로 세입자 화면에 나간다.
- **`walkMinutes`에 검증 구멍이 있다.** 요청 DTO가 `@Min(0) int`라 키가 없으면 Jackson이 `0`을 넣고 `0 >= 0`으로 통과한다. 도메인 검증도 `type`·`name`만 보고 MongoDB validator는 이미 채워진 키를 본다 — **"역까지 도보 0분" 매물이 조용히 저장된다.**

이슈 #224가 제공자를 **카카오 로컬 API**로 지목했다. 도로명 주소(NCP Geocoding, ADR-0042)·장소 후보(네이버 지역 검색)와 함께 **세 번째 지도 계열 외부 연동**이 된다.

> **인근 대학 자동 매핑은 이 ADR의 범위가 아니다.** 같은 연동으로 `nearbyUniversityCodes`(진단 추천의 조인 키)를 좌표에서 파생하는 방안을 함께 검토했으나, **카카오가 주는 대학 이름을 카탈로그 코드로 되돌리는 규칙이 정해지지 않아** 뺐다 — 카카오에 대학 전용 카테고리가 없어 `SC4`(학교)에서 `category_name`으로 걸러야 하는데, 그 뒤 이름 매칭이 `contains`면 `고려대학교사범대학부속고등학교`가 `KOREA`가 되고 `equals`면 캠퍼스 문서(`연세대학교 신촌캠퍼스 제1공학관`)가 통째로 빠진다. **실제 응답 표기를 실측하기 전에는 규칙을 정할 수 없다.** 별도 이슈에서 다룬다.

## Decision

**카카오 로컬 API로 인근 역 검색 엔드포인트 2개를 만든다** — 이름으로 찾는 길과 좌표로 훑는 길이다. 등록 폼 전용이라 임대인만 부를 수 있다.

### 1. 역 검색 — 엔드포인트 2개, 임대인 전용

| Method | Path | 카카오 API |
|---|---|---|
| GET | `/api/v1/listings/stations?keyword=&lat=&lng=` | 키워드로 장소 검색 + `category_group_code=SW8` |
| GET | `/api/v1/listings/stations/nearby?lat=&lng=` | 카테고리로 장소 검색 + `SW8`, 반경 2,000m |

**경로는 `/api/v1`이다.** 매물 데이터를 쓰지 않아 v4 개편의 영향을 받지 않는다 — 장소 후보 검색·주소 검색과 같은 자리다([ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md)).

**인증은 주소 검색과 같다** — `ROLE_USER` + 서비스의 임대인 재검사. 등록 폼 전용 API를 공개로 두면 인증 없이 카카오 쿼터를 소모하는 프록시가 된다. `SecurityConfig`의 `hasRole("USER")` 매처를 **공개 조회 매처보다 먼저** 선언한다 — `/api/v1/listings/stations`는 한 세그먼트라 `GET /api/v1/listings/*` `permitAll`에 잡히고, 아래에 두면 먼저 매칭된 규칙이 이겨 **인증이 통째로 무시된다**(ADR-0042가 겪은 함정 그대로다).

키워드 검색의 `lat`·`lng`는 **선택이되 함께 와야 한다.** 좌표가 있으면 거리순 정렬과 `distanceMeters`·`suggestedWalkMinutes`가 붙는다 — 등록 순서상 주소를 먼저 검색하므로 좌표는 이미 손에 있고, 그래야 동명 역(전국의 `시청역`)을 가려낼 수 있다.

### 2. `suggestedWalkMinutes`는 제안값이지 정답이 아니다

`ceil(distanceMeters / 80)`(최소 1). 80m/분은 부동산 표시·광고의 도보 환산 관행이라 새 상수를 발명하지 않는다.

카카오의 `distance`는 **직선거리**라 실제 보행 경로(육교·지하도·블록)보다 짧게 나온다. 그래서 이름에 `suggested`를 박아 **하한 제안**임을 계약으로 못 박는다. 서버는 이 값을 등록에 강제하지 않는다 — `walkMinutes`는 요청이 보낸 값을 그대로 저장하고, 실제 도보 시간과 맞는지는 승인 심사가 본다.

카카오 모빌리티 길찾기는 **자동차 전용**이라 도보 시간을 주지 않는다.

### 3. `walkMinutes`를 요청 계층에서 조인다

`@Min(0) int` → `@NotNull @Min(0) Integer`. 키 부재가 `null`이 되어 `400 INVALID_INPUT`의 `errors[]`에 실린다. **저장 스키마는 그대로**라 마이그레이션이 없다.

### 4. 포트·어댑터와 설정

두 호출(역 키워드·역 좌표)이 **한 제공자·한 API 계열·한 유스케이스**라 포트와 어댑터를 한 벌로 둔다 — 쪼개면 HTTP 호출·좌표 파싱·에러 래핑이 중복된다. `listing/domain/nearby/NearbyPlaceSearchClient`(포트) ↔ `listing/infrastructure/external/kakao/KakaoLocalPlaceClient`(어댑터).

설정은 `app.kakao.local` 네임스페이스를 새로 판다. **네이버(`app.naver.*`)와 콘솔이 달라 값을 공유할 수 없다.** 인증은 `Authorization: KakaoAK {REST_API_KEY}` 한 줄로, NCP처럼 두 값이 아니다.

자격증명이 없어도 앱은 기동하고 **역 검색 호출만 502**다(장소 검색·주소 검색·SMS·사업자번호 검증과 같은 정책).

카카오 에러 본문의 `code`(`-401`·`-5`·`-10` 등)는 **구분하지 않고 전부 `UPSTREAM_ERROR`로 접는다.** 프론트가 할 수 있는 대응이 "잠시 후 재시도" 하나로 같기 때문이다. 신규 `ErrorCode`도 만들지 않는다(주소 검색과 같은 판단).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. 카카오 로컬 + 엔드포인트 2개(채택)** | 이름·좌표 두 진입점을 모두 주고, 카테고리로 역만 거를 수 있다 | 외부 의존이 하나 는다 | **채택** |
| B. 네이버 지역 검색 재사용 | 이미 연동돼 있다 | 업체명 검색에 가까워 **역만 카테고리로 거를 수 없다** | 미채택 — 카테고리 필터가 이 기능의 핵심이다 |
| C. 역 목록을 자체 시드로 둔다 | 외부 호출이 0이다 | 전국 역을 우리가 관리해야 하고, 그 사전이 곧 [ADR-0043](./0043-remove-seeded-poi-keyword-search.md)에서 지운 부채다 | 미채택 |
| D. 프론트가 카카오를 직접 호출 | 서버가 외부 연동을 안 든다 | 클라이언트에 키가 박히고 쿼터를 통제할 수 없다 | 미채택 — 사진 직접 업로드를 기각한 것과 같은 이유([ADR-0041](./0041-listing-image-upload-to-s3.md) D) |
| E. 엔드포인트 하나로 합치고 파라미터로 분기 | 경로가 하나다 | 필수 파라미터가 요청마다 달라져 계약이 흐려지고, 카카오도 키워드/카테고리로 API가 갈린다 | 미채택 |

## Consequences

- **긍정**: 역 이름이 표준화돼 오타가 사라진다. `walkMinutes`의 조용한 `0` 저장이 막힌다. 요청·저장 스키마가 그대로라 마이그레이션이 없다.
- **부정/트레이드오프**
  - **외부 의존이 하나 는다.** 카카오가 죽으면 역을 새로 검색할 수 없다(진행 중인 폼은 이미 받은 이름으로 제출할 수 있다). **등록 경로에는 외부 호출이 붙지 않는다** — ADR-0042 §2와 같은 원칙이다.
  - **`walkMinutes` 필수화가 기존 요청을 400으로 만든다.** 문서상 이미 "필수"였고 값이 조용히 0으로 저장되던 버그지만, 배포 순서 합의는 필요하다.
  - **환승역이 여러 건으로 온다**(`신촌역 2호선`·`신촌역 경의중앙선`). 노선이 보이는 게 선택에 도움이 되므로 합치지 않는다 — 다만 그 표기가 그대로 세입자 화면에 나간다.
  - **영문 역명이 없다.** 등록은 한국어 한 값만 받아 `en`에 복사하므로 영어 화면에 한국어 역명이 저장된다(승인 심사에서 번역). `ListingResponseMapper`의 `" Station"→" Sta."` 축약은 당분간 동작하지 않는다 — 기존과 같은 상태다.
  - **자격증명이 콘솔별로 셋**이 된다(네이버 Developers · NCP · 카카오).
- **후속 작업**
  - **인근 대학 자동 매핑**(별도 이슈) — 카카오 응답의 실제 표기를 실측해 이름→코드 매칭 규칙을 먼저 정한다. 이 ADR이 깐 포트·어댑터·자격증명을 그대로 쓴다.
  - 카카오 일일 쿼터 실측 후 반경 조정.
  - 요금 필드(`PricingRequest`의 `monthlyRent`·`deposit`·`maintenanceFee`)에 `walkMinutes`와 **같은 검증 구멍**이 남아 있다 — 누락 시 조용히 `0`이 된다. 금액 필드라 계약 변경 폭이 달라 별도 이슈로 분리한다.

## Validation

- `GET /api/v1/listings/stations?keyword=신촌` + 매물 좌표를 보내면 신촌역이 거리순 상위에 오고 `distanceMeters`·`suggestedWalkMinutes`가 채워진다. 좌표를 빼면 두 필드가 `null`이다.
- 좌표를 하나만 보내면 `400 INVALID_INPUT`이다.
- `GET /api/v1/listings/stations/nearby`에 홍대 좌표를 주면 홍대입구역·합정역이 가까운 순으로 온다.
- `walkMinutes`를 빼고 등록하면 `400 INVALID_INPUT`이고 `errors[]`에 `nearestTransit.walkMinutes`가 실린다.
- 세입자 토큰으로 역 검색을 부르면 `403 FORBIDDEN`, 토큰 없이 부르면 `401 UNAUTHENTICATED`다(공개 매처에 걸려 200이 나오지 않는다).
- `KAKAO_REST_API_KEY`를 비우면 역 검색만 `502 UPSTREAM_ERROR`이고 다른 기능은 정상이다. 이때 **카카오로 나가는 요청 자체가 없다.**
