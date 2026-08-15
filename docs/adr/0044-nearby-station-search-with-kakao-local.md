# ADR-0044. 인근 역은 카카오 로컬로 검색하고, 인근 대학은 등록이 스스로 파생한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0044 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-15 |
| 기준 코드 | `feature/224-nearby-station-search-api` @ `1e50052`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0028](./0028-diagnosis-questions-catalog-store.md), [ADR-0037](./0037-listing-localization-and-code-catalog.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [ADR-0041](./0041-listing-image-upload-to-s3.md), [ADR-0042](./0042-road-address-search-with-ncp-geocoding.md), [listing API](../api/specs/03-listings-favorites.md) |

## Status

Proposed

## Context

매물 등록 폼의 "주변" 정보 두 칸이 서로 다른 이유로 비어 있거나 신뢰할 수 없다.

- **인근 역은 자유 입력이다.** `nearestTransit.name`은 임대인이 친 문자열을 그대로 저장하며 어떤 사전과도 대조하지 않는다([ListingRegisterService](../../src/main/java/com/kohere/listing/application/ListingRegisterService.java)). 오타가 그대로 세입자 화면에 나간다.
- **인근 대학은 항상 빈 배열이다.** 등록 요청에 담을 필드 자체가 없어 서버가 `Set.of()`를 박는다. 그런데 이 값은 진단 추천이 매물을 찾는 **조인 키**다 — [ListingRepositoryImpl](../../src/main/java/com/kohere/listing/infrastructure/persistence/ListingRepositoryImpl.java)의 `Criteria.where("nearbyUniversityCodes").in(universityCodes)`([ADR-0028](./0028-diagnosis-questions-catalog-store.md)). **지금 등록되는 매물은 어떤 진단 결과에도 걸리지 않는다.** [ADR-0039](./0039-listing-schema-v4-registration-form.md)와 [ADR-0042](./0042-road-address-search-with-ncp-geocoding.md)가 "좌표 기반 파생"을 후속으로 남긴 자리이고, ADR-0042가 `location`을 채우면서 **그 전제가 갖춰졌다.**
- **`walkMinutes`에 검증 구멍이 있다.** 요청 DTO가 `@Min(0) int`라 키가 없으면 Jackson이 `0`을 넣고 `0 >= 0`으로 통과한다. 도메인 검증도 `type`·`name`만 보고 MongoDB validator는 이미 채워진 키를 본다 — **"역까지 도보 0분" 매물이 조용히 저장된다.**

이슈 #224가 제공자를 **카카오 로컬 API**로 지목했다. 도로명 주소(NCP Geocoding, ADR-0042)·장소 후보(네이버 지역 검색)와 함께 **세 번째 지도 계열 외부 연동**이 된다.

## Decision

**역은 검색 엔드포인트를 주고, 대학은 등록이 서버 스스로 파생한다.** 둘 다 카카오 로컬 API를 쓰지만 노출 방식이 정반대다.

### 1. 왜 대학만 엔드포인트가 없나

| | 인근 역 (`nearestTransit.name`) | 인근 대학 (`nearbyUniversityCodes`) |
|---|---|---|
| 정체 | **표시용 문자열** — 상세 화면의 교통 배지 | **검색 키** — 진단 추천의 `$in` 매칭 대상 |
| 값의 주인 | 임대인이 고르고 책임진다 | **서버가 소유한다** |
| 허용 값 | 열린 집합(어떤 역 이름이든) | **닫힌 코드 집합** — 카탈로그 `UNIVERSITY` 14종 |
| 틀렸을 때 | 상세 화면에 오타가 보인다 | **추천 결과가 조용히 오염된다** |

여기에 더해 **클라이언트에 대학 선택 폼 자체가 없다.** 고를 화면이 없는데 후보 목록 API를 노출하면 소비처 없는 공개 표면과 카카오 쿼터를 태울 경로만 늘어난다. 그리고 애초에 고를 값이 아니다 — 임대인이 고르게 하면 "우리 매물 서울대 근처"라는 주장이 추천에 실린다. **좌표가 사실을 정한다.**

### 2. 역 검색 — 엔드포인트 2개, 임대인 전용

| Method | Path | 카카오 API |
|---|---|---|
| GET | `/api/v1/listings/stations?keyword=&lat=&lng=` | 키워드로 장소 검색 + `category_group_code=SW8` |
| GET | `/api/v1/listings/stations/nearby?lat=&lng=` | 카테고리로 장소 검색 + `SW8`, 반경 2,000m |

**경로는 `/api/v1`이다.** 매물 데이터를 쓰지 않아 v4 개편의 영향을 받지 않는다 — 장소 후보 검색·주소 검색과 같은 자리다([ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md)).

**인증은 주소 검색과 같다** — `ROLE_USER` + 서비스의 임대인 재검사. 등록 폼 전용 API를 공개로 두면 인증 없이 카카오 쿼터를 소모하는 프록시가 된다. `SecurityConfig`의 `hasRole("USER")` 매처를 **공개 조회 매처보다 먼저** 선언한다 — `/api/v1/listings/stations`는 한 세그먼트라 `GET /api/v1/listings/*` `permitAll`에 잡히고, 아래에 두면 먼저 매칭된 규칙이 이겨 **인증이 통째로 무시된다**(ADR-0042가 겪은 함정 그대로다).

키워드 검색의 `lat`·`lng`는 **선택이되 함께 와야 한다.** 좌표가 있으면 거리순 정렬과 `distanceMeters`·`suggestedWalkMinutes`가 붙는다 — 등록 순서상 주소를 먼저 검색하므로 좌표는 이미 손에 있고, 그래야 동명 역(전국의 `시청역`)을 가려낼 수 있다.

### 3. 대학 파생 — 등록 중 best-effort

```text
POST /api/v2/listings
  └─ address.lat/lng로 카카오 SC4 카테고리 검색 1회
       ├─ 카탈로그가 아는 대학 있음 → 그 코드들을 저장
       ├─ 반경 내 없음            → []
       └─ 카카오 오류·타임아웃·키 미설정 → []  (등록은 성공, WARN 로그)
```

**요청 계약이 바뀌지 않는다.** 프론트 배포 순서 합의가 필요 없다.

**[ADR-0042](./0042-road-address-search-with-ncp-geocoding.md) §2와 어긋나지 않는다.** 그 ADR은 "등록 시점에 재지오코딩하지 않는다"고 정했는데, 근거는 *"그때의 외부 장애가 곧 등록 실패(502)가 된다"* 였다. **이 파생은 실패해도 등록이 죽지 않는다** — 주소 좌표는 없으면 매물이 성립하지 않는 필수값이라 실패가 곧 등록 실패였지만, `nearbyUniversityCodes`는 **빈 배열이 이미 유효한 상태**다. 규칙을 이렇게 갈라 둔다:

> **필수값은 클라이언트가 되돌려 받고, 파생값은 서버가 best-effort로 채운다.**

**반경 내 대학을 전부 담는다**(가장 가까운 하나가 아니다). 필드가 복수형 `Set<String>`이고 진단 추천이 `$in`이라 담긴 수만큼 매칭 기회가 는다 — 신촌 매물이 연세대만 달면 이화여대를 고른 진단 결과에 걸리지 않는다.

**등록 경로는 `page=1` 한 번만 부르고 재시도하지 않는다.** 등록은 임대인이 제출 버튼을 누르고 기다리는 마지막 단계라, 외부 왕복을 늘리지 않는다. read timeout도 검색(5,000ms)과 분리해 **2,000ms**로 자른다.

### 4. 대학 판별 — `category_name`을 계층으로 파싱한다

카카오에는 **대학 전용 카테고리 코드가 없다.** `SC4`(학교)가 초·중·고를 함께 담는다. 그래서 `category_name`(` > `로 이어진 계층 문자열)을 쪼개 판별한다.

```text
교육,학문 > 학교 > 대학교     ← 대학
교육,학문 > 학교 > 고등학교   ← 제외
교육,학문 > 학교 > 초등학교   ← 제외
```

**규칙**: `>`로 split → 각 세그먼트 trim → 어느 하나라도 `"대학교"`를 **포함**하면 대학.

- `equals`가 아니라 `contains`인 이유: 카카오가 하위 단계(`… > 대학교 > 사립대학교`)를 추가해도 계속 걸린다. `"고등학교"`·`"중학교"`·`"초등학교"`는 `"대학교"`를 부분 문자열로 포함하지 않아 오탐이 없다.
- 전체 문자열이 아니라 세그먼트 단위인 이유: 상위 분류에 우연히 `"대학교"`가 섞인 경우를 걸러 낸다.

**4년제만 넣는다** — `… > 대학원`·`… > 전문대학`은 `"대학교"`를 포함하지 않아 빠진다. 조건을 `"대학"`으로 넓히면 2년제와 함께 **대학원도 들어온다.** 파생값의 정확도는 승인 심사가 보정하므로(§6) 그 부작용을 감수하지 않는다.

### 5. 이름 정규화·중복 제거·코드 매핑

카카오는 캠퍼스·건물을 각각 별도 문서로 준다(`연세대학교`, `연세대학교 신촌캠퍼스 제1공학관`, …).

1. **정규화** — `place_name`에서 첫 `"대학교"`까지만 남긴다(`연세대학교 신촌캠퍼스 제1공학관` → `연세대학교`).
2. **중복 제거** — 정규화한 이름으로 묶어 하나만 남긴다. `sort=distance`라 첫 항목이 곧 최단거리다.
3. **코드 매핑** — `place_name`에 `listingCatalog`의 `UNIVERSITY` `label.ko`가 **포함**되면 그 코드를 취하고, 못 찾으면 버린다.

3번은 **코드베이스가 이미 쓰는 방식이다.** [ListingCatalogCodes](../../src/main/java/com/kohere/listing/application/ListingCatalogCodes.java)가 도로명 주소에서 `City`·`District`를 뽑을 때 같은 `contains` 매칭을 한다 — 새 규칙이 아니라 `UNIVERSITY` 카테고리로 한 줄 넓히는 것이다. 14개 라벨 중 서로의 부분 문자열인 것이 없어 모호성도 없다.

**필터 순서가 안전장치다.** `contains`만으로는 `고려대학교사범대학부속고등학교`가 `KOREA`로 잡히지만, 그 문서의 `category_name`은 `… > 학교 > 고등학교`라 §4에서 먼저 빠진다. `서울대학교병원`은 `SC4`가 아니라 `HP8`이라 그룹 코드 단계에서 빠진다.

```text
① category_group_code == SC4 → ② category_name 세그먼트에 "대학교" → ③ place_name contains label.ko
```

### 6. 파생 대학의 누락·부정확은 관리자 승인 심사가 흡수한다

등록 직후 상태는 `PENDING`이고 세입자 조회 어디에도 노출되지 않는다. 심사가 인근 대학을 다시 확인하므로 서버 파생은 **"심사자가 손볼 초안"** 이면 충분하다. [ADR-0042](./0042-road-address-search-with-ncp-geocoding.md) §2가 **좌표 위조**를 정확히 같은 방식으로 흡수한 것과 같다.

그래서 **백필 배치를 만들지 않는다.** 카카오 장애 중 `[]`로 저장된 매물도 승인 대기 큐에 그대로 있다. 자동 재시도를 두면 심사와 배치가 같은 필드를 두고 경합한다.

> **승인 API에 요구사항이 하나 붙는다.** [ADR-0039](./0039-listing-schema-v4-registration-form.md)가 예고한 승인 API의 요구사항은 상태 전환과 `location` 보유 게이트뿐이다 — **필드 편집이 아니다.** 상태만 뒤집는 승인으로는 이 흡수가 성립하지 않으므로, 승인 API는 `nearbyUniversityCodes`를 **심사자가 편집할 수 있어야 한다.**

### 7. `suggestedWalkMinutes`는 제안값이지 정답이 아니다

`ceil(distanceMeters / 80)`(최소 1). 80m/분은 부동산 표시·광고의 도보 환산 관행이라 새 상수를 발명하지 않는다.

카카오의 `distance`는 **직선거리**라 실제 보행 경로(육교·지하도·블록)보다 짧게 나온다. 그래서 이름에 `suggested`를 박아 **하한 제안**임을 계약으로 못 박고, 등록의 `walkMinutes`는 여전히 임대인이 보낸 값을 그대로 저장한다. 폼은 이 값을 입력칸 기본값으로 채우고 수정 가능하게 둔다 — 값의 책임은 임대인에게 남는다(승인 심사가 보는 대상이다).

카카오 모빌리티 길찾기는 **자동차 전용**이라 도보 시간을 주지 않는다.

### 8. `walkMinutes`를 요청 계층에서 조인다

`@Min(0) int` → `@NotNull @Min(0) Integer`. 키 부재가 `null`이 되어 `400 INVALID_INPUT`의 `errors[]`에 실린다. **저장 스키마는 그대로**라 마이그레이션이 없다.

### 9. 포트·어댑터와 설정

세 호출(역 키워드·역 좌표·대학 좌표)이 **한 제공자·한 API 계열·한 유스케이스**라 포트와 어댑터를 한 벌로 둔다 — 셋으로 쪼개면 HTTP 호출·좌표 파싱·에러 래핑이 3중복된다. `listing/domain/nearby/NearbyPlaceSearchClient`(포트) ↔ `listing/infrastructure/external/kakao/KakaoLocalPlaceClient`(어댑터).

설정은 `app.kakao.local` 네임스페이스를 새로 판다. **네이버(`app.naver.*`)와 콘솔이 달라 값을 공유할 수 없다.** 인증은 `Authorization: KakaoAK {REST_API_KEY}` 한 줄로, NCP처럼 두 값이 아니다.

자격증명이 없어도 앱은 기동한다 — **역 검색만 502**이고 **등록은 정상이되 `nearbyUniversityCodes`가 `[]`** 다.

카카오 에러 본문의 `code`(`-401`·`-5`·`-10` 등)는 **구분하지 않고 전부 `UPSTREAM_ERROR`로 접는다.** 프론트가 할 수 있는 대응이 "잠시 후 재시도" 하나로 같기 때문이다. 신규 `ErrorCode`도 만들지 않는다(주소 검색과 같은 판단).

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. 역=엔드포인트 · 대학=등록 파생(채택)** | 각 값의 성격에 맞고 요청 계약이 안 바뀐다 | 등록 경로에 외부 호출이 하나 는다 | **채택** |
| B. 대학도 검색 엔드포인트로 노출 | 폼이 미리 보여줄 수 있다 | **클라이언트에 대학 폼이 없다.** 소비처 없는 공개 표면과 쿼터 소모 경로만 는다 | 미채택 — §1 |
| C. 등록 요청이 `nearbyUniversityCodes`를 보낸다 | 서버가 외부를 안 부른다 | 조인 키를 클라이언트가 정한다 — "우리 매물 서울대 근처" 주장이 추천에 실린다. 요청 계약도 깨진다 | 미채택 — §1 |
| D. `searchPlaces` 시드 좌표로 자체 계산 | 외부 호출이 0이고 14개뿐이라 인메모리로 충분하다 | 그 컬렉션은 [ADR-0043](./0043-remove-seeded-poi-keyword-search.md)으로 **제거됐다**. 되살리면 카탈로그와 이중 관리가 부활한다 | 미채택 |
| E. 네이버 지역 검색 재사용 | 이미 연동돼 있다 | 업체명 검색에 가까워 역·대학을 카테고리로 거를 수 없다 | 미채택 — 카테고리 필터가 이 기능의 핵심이다 |
| F. 프론트가 카카오를 직접 호출 | 서버가 외부 연동을 안 든다 | 클라이언트에 키가 박히고 쿼터를 통제할 수 없다 | 미채택 — 사진 직접 업로드를 기각한 것과 같은 이유([ADR-0041](./0041-listing-image-upload-to-s3.md) D) |
| G. 가장 가까운 대학 1개만 저장 | 단순하다 | 필드가 복수형이고 `$in` 매칭이라 매칭 기회가 1/N로 준다 | 미채택 — §3 |

## Consequences

- **긍정**: 역 이름이 표준화돼 오타가 사라진다. **등록 매물이 진단 추천에 처음으로 걸린다** — ADR-0039가 남긴 절반이 채워졌다. `walkMinutes`의 조용한 `0` 저장이 막힌다. 요청·저장 스키마가 그대로라 마이그레이션과 프론트 배포 순서 합의가 없다.
- **부정/트레이드오프**
  - **외부 의존이 하나 는다.** 카카오가 죽으면 역을 새로 검색할 수 없다(진행 중인 폼은 이미 받은 이름으로 제출할 수 있다). 대학은 `[]`가 된다.
  - **등록 응답이 최대 2초 느려진다**(§3의 read timeout).
  - **`walkMinutes` 필수화가 기존 요청을 400으로 만든다.** 문서상 이미 "필수"였고 값이 조용히 0으로 저장되던 버그지만, 배포 순서 합의는 필요하다.
  - **`SC4`에 초·중·고가 섞여 대학이 밀릴 수 있다.** 반경 2km 안에 학교가 15개(카카오 `size` 상한)를 넘으면 대학교가 2페이지로 넘어가는데 등록 파생은 1페이지만 본다. 누락은 승인 심사가 보정한다.
  - **환승역이 여러 건으로 온다**(`신촌역 2호선`·`신촌역 경의중앙선`). 노선이 보이는 게 선택에 도움이 되므로 합치지 않는다(대학과 반대 처리다) — 다만 그 표기가 그대로 세입자 화면에 나간다.
  - **영문 역명이 없다.** 등록은 한국어 한 값만 받아 `en`에 복사하므로 영어 화면에 한국어 역명이 저장된다(승인 심사에서 번역). `ListingResponseMapper`의 `" Station"→" Sta."` 축약은 당분간 동작하지 않는다 — 기존과 같은 상태다.
  - **자격증명이 콘솔별로 셋**이 된다(네이버 Developers · NCP · 카카오).
- **후속 작업**
  - 승인 API에서 `nearbyUniversityCodes` 편집(§6).
  - 카카오 일일 쿼터 실측 후 반경·페이지 상한 조정.
  - 요금 필드(`PricingRequest`의 `monthlyRent`·`deposit`·`maintenanceFee`)에 `walkMinutes`와 **같은 검증 구멍**이 남아 있다 — 누락 시 조용히 `0`이 된다. 금액 필드라 계약 변경 폭이 달라 별도 이슈로 분리한다.

## Validation

- `GET /api/v1/listings/stations?keyword=신촌` + 매물 좌표를 보내면 신촌역이 거리순 상위에 오고 `distanceMeters`·`suggestedWalkMinutes`가 채워진다. 좌표를 빼면 두 필드가 `null`이다.
- 좌표를 하나만 보내면 `400 INVALID_INPUT`이다.
- `GET /api/v1/listings/stations/nearby`에 홍대 좌표를 주면 홍대입구역·합정역이 가까운 순으로 온다.
- 신촌 좌표로 등록하면 응답의 `nearbyUniversityCodes`가 `["YONSEI","EWHA"]`이고 **초·중·고 코드가 섞이지 않는다.**
- 카카오가 예외를 던져도 등록은 `201`이고 `nearbyUniversityCodes`가 `[]`다 — 등록이 죽지 않는다.
- `walkMinutes`를 빼고 등록하면 `400 INVALID_INPUT`이고 `errors[]`에 `nearestTransit.walkMinutes`가 실린다.
- 세입자 토큰으로 역 검색을 부르면 `403 FORBIDDEN`, 토큰 없이 부르면 `401 UNAUTHENTICATED`다(공개 매처에 걸려 200이 나오지 않는다).
- `KAKAO_REST_API_KEY`를 비우면 역 검색만 `502 UPSTREAM_ERROR`이고 등록은 `201` + `[]`다. 이때 **카카오로 나가는 요청 자체가 없다.**
