# ADR-0043. 시드 POI 키워드 검색을 종료하고 `searchPlaces`를 버린다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0043 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-15 |
| 기준 코드 | `feature/224-nearby-station-search-api` @ `1815746`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0032](./0032-mongodb-migration-runner.md), [ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md), [listing API](../api/specs/03-listings-favorites.md), [US-3-3](../requirements/user-stories.md) |

## Status

Proposed

## Context

`GET /api/v2/listings/search`(및 v1 스텁)는 MongoDB `searchPlaces` 컬렉션에 시드된 POI 38건(대학 14·지하철역 12·지역 12)을 인메모리로 점수화해 검색어를 좌표로 바꾸고, 그 좌표 주변 매물을 함께 돌려주는 API다.

**이 경로는 이미 대체됐다.**

- **유저 스토리가 요구하지 않는다.** US-3-3의 제목은 **"네이버 장소 검색 및 주변 매물 조회"** 이고 AC 6개 중 `/listings/search`를 부르는 것이 하나도 없다. 전부 `GET /api/v1/listings/places`(네이버 지역 검색) → 앱이 bounds 계산 → `/api/v2/listings`·`/api/v2/listings/map` 흐름이다.
- **시퀀스 다이어그램도 마찬가지다.** 파일 이름은 `us-3-3-keyword-search.md`인데 내용은 네이버 `places` 흐름만 그린다. `searchPlaces`도 `/listings/search`도 등장하지 않는다.
- **설계 의도가 이미 갈아탄 상태다.** `ListingPlaceController`의 주석이 직접 말한다 — *"기존 `/api/v1/listings/search`의 POI·주변 매물 검색 계약을 변경하지 않기 위해 별도 리소스로 분리한다."* `places`는 `search`의 후계자로 만들어졌고 옛 경로만 치우지 않았다.
- **클라이언트가 v1·v2 어느 쪽도 호출하지 않는다**(팀 확인).

한편 시드 사전은 **운영 부채**이기도 하다. 같은 대상(대학·지역)을 `listingCatalog`와 따로 들고 있으면서 둘을 잇는 선언이 없어, 지역 쪽은 이미 양방향으로 어긋나 있다 — `성북구`·`동작구`·`성동구`는 검색되지만 등록할 수 없는 지역이고, `종로구`·`구로구`·`영등포구`·`금천구`는 등록 가능한데 이름으로 찾을 수 없다.

## Decision

**키워드 검색 API(v1·v2)와 `searchPlaces` 컬렉션을 함께 제거한다.**

### 1. v1 스텁도 함께 지운다

[ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md)은 구버전 앱이 "매물 없음" 화면에 도달하도록 v1 조회 6종을 빈 결과 스텁으로 남겼다. 그중 키워드 검색만 뺀다 — **클라이언트가 호출하지 않는 것이 확인됐으므로** 스텁을 유지할 이유가 없다. 조회 스텁은 5종이 된다.

제거 후 그 경로는 `404 LISTING_NOT_FOUND`가 된다. 두 컨트롤러 모두 `@GetMapping("/{listingId}")`를 갖고 있어 `listingId = "search"`로 매칭되기 때문이다. 없어진 엔드포인트가 404를 내는 것은 당연한 동작이라 스펙에 따로 적지 않는다.

### 2. 드롭을 사람이 아니라 Mongock이 한다

[ListingMongoIndexInitializer](../../src/main/java/com/kohere/listing/infrastructure/persistence/ListingMongoIndexInitializer.java)는 마이그레이션이 아니라 **매 기동마다 도는 `ApplicationRunner`** 이고, `indexOps(SearchPlaceDocument.class).createIndex(...)`는 대상 컬렉션이 없으면 **만든다.** 그래서 수동으로 드롭해도 다음 기동에 빈 컬렉션이 인덱스와 함께 되살아난다.

수동 드롭은 환경(dev·prod·개발자 로컬)마다 사람이 `mongosh`를 붙어야 하고, 코드 배포보다 먼저 실행하면 옛 코드의 재기동이 컬렉션을 되살린다 — **순서를 사람이 지켜야 한다.**

그래서 [ADR-0032](./0032-mongodb-migration-runner.md)가 정한 도구를 쓴다. 드롭을 `@ChangeUnit`(`listing-search-place-drop`, order `0117`)으로 코드화하면 배포 아티팩트에 함께 실려 가 각 환경이 자기 기동 때 정확히 1회 적용한다.

한 번의 기동 안에서도 순서가 맞다. `application.yml`이 Mongock을 `runner-type: InitializingBean`으로 두고 있고(주석: *"마이그레이션은 로컬 fixture·인덱스 등 ApplicationRunner보다 먼저 끝나야 한다"*), Spring 생명주기상 `InitializingBean`이 `ApplicationRunner`보다 앞이다.

```text
앱 기동
  ├─ [InitializingBean]  Mongock 0117            → searchPlaces 드롭
  └─ [ApplicationRunner] ListingMongoIndexInitializer → searchPlaces 블록이 이미 없음 → 재생성 없음
```

**단 이는 드롭 유닛 추가와 인덱스 블록 제거가 같은 커밋일 때만 성립한다.** 갈라지면 같은 기동 안에서 드롭 → 재생성이 일어나 아무 효과가 없다.

### 3. `listingCatalog`는 손대지 않는다

시드가 사라지면 대학 정보를 옮겨야 하는지 확인했다. **옮길 것이 없다.**

| | `searchPlaces`(UNIVERSITY 14) | `listingCatalog`(UNIVERSITY 14) |
|---|---|---|
| 코드 | `UNIV_SNU` … | `SNU` … — 14개 1:1 |
| 한국어명 | `name` | `label.ko` — **14개 문자열까지 완전 일치** |
| 영어명 | 없음 | `label.en`(`Seoul National Univ.`) |
| 좌표 | 있음 | 없음 |
| `aliases`·`priority`·`active` | 있음 | 없음 |

카탈로그가 이미 상위집합이다. 좌표는 버리기로 했고, `aliases`·`priority`·`active`는 **유일한 소비자가 `SearchPlaceMatcher`** 였으므로 그것과 함께 사라진다. 카탈로그에 추가할 필드가 하나도 없다.

### 4. 네이버 장소 검색은 그대로 둔다

`listing/domain/place/` 패키지에는 성격이 다른 두 묶음이 섞여 있다. **`PlaceSearchClient`·`PlaceSearchResult`·`PlaceSearchUpstreamException`(네이버 지역 검색)은 남기고**, `SearchPlace`·`SearchPlaceType`·`SearchPlaceRepository`(시드 사전)만 지운다. `GET /api/v1/listings/places`는 US-3-3의 정본 경로다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. API·컬렉션 함께 제거(채택)** | 죽은 코드와 운영 부채가 함께 사라진다 | 타입 가중치·별칭 사전을 잃는다 | **채택** |
| B. 유지 | 아무 작업이 없다 | 아무도 안 쓰는 API와 시드를 계속 관리·배포한다. 지역 드리프트도 남는다 | 미채택 — 유지 비용만 남는다 |
| C. 시드만 비우고 API는 유지 | 컬렉션 관리가 사라진다 | API가 **항상 "결과 없음"** 을 주는 껍데기가 된다. 살아 있는 척하는 스텁이 더 나쁘다 | 미채택 |
| D. 키워드 검색을 카카오로 교체 | 좌표·거리를 외부에서 얻어 시드 없이 같은 기능 유지 | 타입 가중치·별칭·운영 플래그는 여전히 못 살린다. **쓰는 곳이 없는 API를 새로 만드는 셈** | 미채택 — 소비처가 생기면 그때 판단한다 |
| E. 드롭을 수동으로 | changeUnit 하나가 준다 | 환경마다 사람이 붙어야 하고 순서를 지켜야 한다(§2) | 미채택 |

## Consequences

- **긍정**: 아무도 부르지 않는 엔드포인트 2개, 클래스 13개, 컬렉션 1개, 시드 38건이 사라진다. `searchPlaces`와 `listingCatalog` 사이의 드리프트도 함께 사라진다. 컬렉션 드롭에 수동 절차가 없다.
- **부정/트레이드오프** — 되살리려면 시드부터 다시 만들어야 한다:
  - **타입 가중치 정렬을 잃는다.** "신촌"에 대학(30) > 역(20) > 지역(10)으로 매기던 규칙이 사라진다. 네이버·카카오 모두 이 개념이 없어 순위가 제공자 정책을 따른다.
  - **별칭 사전을 잃는다.** `연세`·`연세대`·`yonsei` → 연세대학교 매핑을 우리가 통제하지 못한다.
  - **`priority`·`active` 운영 플래그를 잃는다.** 특정 POI를 검색에서 잠시 빼는 수단이 없다.
  - **"장소 + 주변 매물"이 한 번의 호출이 아니게 된다.** 프론트가 `places` → bounds 계산 → `map` 2단계로 부른다. US-3-3 AC가 이미 그렇게 규정하고 있어 실질 변화는 없다.
  - **코드를 롤백하면 빈 컬렉션이 부활한다.** 옛 코드의 인덱스 초기화기가 다시 만들기 때문이다. 시드는 돌아오지 않으므로(`0100`이 실행 완료로 기록돼 있다) 상태는 "빈 컬렉션 + 항상 결과 없는 검색"이며 기동 실패는 아니다. 롤백은 예외 상황이라 방어하지 않는다.
- **후속 작업**: 없다. 지도 키워드 검색이 다시 필요해지면 대안 D를 그때 판단한다.

## Validation

- `GET /api/v2/listings/search`·`GET /api/v1/listings/search`가 `404 LISTING_NOT_FOUND`다.
- `GET /api/v1/listings/places`는 그대로 동작한다(네이버 연동은 건드리지 않았다).
- 앱을 처음 기동하면 `searchPlaces` 컬렉션이 만들어지지 않는다. 이미 있던 환경에서는 기동 후 사라진다.
- **재기동해도 되살아나지 않는다** — `mongosh`에서 `db.getCollectionNames()`에 `searchPlaces`가 없다.
- 새 환경(빈 `changelog`)에서도 `0117`이 없는 컬렉션을 드롭하려다 실패하지 않는다.
- 매물 목록·지도·상세·찜·최근 본·등록은 영향이 없다.
