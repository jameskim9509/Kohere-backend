# ADR-0042. 도로명 주소는 NCP Geocoding으로 검색하고, 등록이 그 좌표를 받는다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0042 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-14 |
| 기준 코드 | `feature/223-road-address-search-api` @ `188d9b8`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0023](./0023-secrets-in-ssm-parameter-store.md), [ADR-0037](./0037-listing-localization-and-code-catalog.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md), [ADR-0041](./0041-listing-image-upload-to-s3.md), [listing API](../api/specs/03-listings-favorites.md) |

## Status

Proposed

## Context

- 등록 폼의 주소 칸은 **자유 입력**이다. 서버는 받은 문자열을 그대로 저장하고(`address.fullAddress`), 거기서 `City`·`District`만 문자열 포함 검사로 뽑는다([ListingCatalogCodes](../../src/main/java/com/kohere/listing/application/ListingCatalogCodes.java)). 표준 표기가 아니면 파싱이 실패해 `400 LISTING_INVALID_ADDRESS`가 나가는데, 사용자는 **무엇을 어떻게 고쳐야 하는지 알 수 없다.**
- **좌표가 비어 있다.** ADR-0039가 `location`·`nearbyUniversityCodes`를 후속으로 남겼고, 그래서 등록된 매물은 좌표 없이 저장된다. 좌표 없는 매물은 지도 마커·거리 정렬에 오르지 못하며, 관리자 승인 조건이 "좌표 보유"라 **승인 전에 누군가 좌표를 채워 넣어야 한다** — 그 경로가 없다.
- 이슈 #223이 제공자를 **NCP(네이버 클라우드 플랫폼) Maps Geocoding**으로 지목했다. Naver Developers 지역 검색은 "도로명 자체를 검색하기 어렵고 업체명 검색에 가깝다"는 이유로 기각됐다.
- 이미 네이버 지역 검색 연동이 있다(`PlaceSearchClient` ↔ `NaverPlaceSearchClient`). 포트/어댑터 모양과 자격증명 주입 경로(SSM SecureString → env, [ADR-0023](./0023-secrets-in-ssm-parameter-store.md))를 그대로 쓸 수 있다. 다만 **콘솔이 다르다** — 지역 검색은 Naver Developers, Geocoding은 NCP다. 키를 공유할 수 없다.
- 사진은 이미 **"미리 올려 키로 참조하는" 2단계**다([ADR-0041](./0041-listing-image-upload-to-s3.md)). 주소도 같은 모양을 취할 수 있다.
- **등록 가능한 지역이 좁다.** 카탈로그의 `CITY`는 3종(서울·부산·경기)이지만 `DISTRICT`는 서울 자치구 9종뿐이고, 등록은 둘 **모두** 파싱에 성공해야 통과한다. 지오코딩은 전국을 돌려준다.

## Decision

**도로명 주소 검색을 별도 엔드포인트로 만들고, 그 응답의 좌표를 등록 요청이 그대로 되돌려 보낸다.**

### 1. 두 단계로 나눈다

| 단계 | 엔드포인트 | 인증 | 횟수 |
|---|---|---|---|
| 주소 검색 | `GET /api/v1/listings/addresses` | 임대인 | 폼에서 필요한 만큼 |
| 등록 | `POST /api/v2/listings` | 임대인 | 1회 |

검색 응답 항목은 `{ roadAddress, jibunAddress, englishAddress, lat, lng }`이고([ADR-0046](./0046-administrative-region-as-catalog-data.md)으로 `supported`가 빠졌다), 등록은 그중 `roadAddress`·`lat`·`lng`를 `address.fullAddress`·`address.lat`·`address.lng`에 담아 되돌려 보낸다. **사진 키와 같은 패턴이다** — 앞선 호출이 준 값을 클라이언트가 보관했다가 그대로 실어 보낸다.

**경로는 `/api/v1`이다.** 신규 계약이라 깨질 하위 호환이 없고(api-design-guide §2-1), 매물 스키마와 무관해 v4 개편의 영향을 받지 않는다 — 같은 이유로 `/api/v1`에 남은 장소 후보 검색(`/listings/places`)과 같은 자리다([ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md)).

**단, 장소 후보 검색과 달리 인증이 필요하다.** 등록 폼 전용 API를 공개로 두면 인증 없이 NCP 호출 쿼터를 소모하는 **지오코딩 프록시**가 된다. `SecurityConfig`에 `hasRole("USER")` 명시 매처를 두되, 기존 `GET /api/v1/listings/*` `permitAll` 매처보다 **위**에 선언한다(먼저 매칭된 규칙이 이긴다 — 아래에 두면 규칙이 통째로 무시된다). 임대인 여부는 매처로 표현할 수 없으므로 서비스가 `user::api`로 재검사해 `403 FORBIDDEN`으로 거른다(등록·사진 업로드와 같은 이중 인가).

### 2. 등록 시 다시 지오코딩하지 않는다

좌표를 **클라이언트가 되돌려 보낸다.** 서버가 등록 시점에 `fullAddress`로 재조회하면 등록마다 외부 왕복이 붙고, 그때의 외부 장애가 곧 **등록 실패**(502)가 된다 — 사진·카탈로그 검증을 모두 통과한 요청이 남의 사정으로 죽는다.

좌표 위조는 가능하다. 그러나 등록 직후 상태는 `PENDING`이고 **관리자 승인이 주소와 좌표를 함께 본다** — 사업자등록번호 진위·영어 번역과 같은 자리에서 걸러진다([ADR-0039](./0039-listing-schema-v4-registration-form.md) §3). 검증을 위해 매 등록에 외부 호출을 붙이는 비용이 그 위험보다 크다.

### 3. 지원 지역 여부는 `supported` 불리언 하나로 알린다

검색은 전국을 돌려주지만 등록은 카탈로그(서울 9개 구)만 통과한다. 알려주지 않으면 임대인이 부산 주소를 골라 폼을 다 채운 뒤에야 `400 LISTING_INVALID_ADDRESS`를 본다.

그래서 각 항목에 **`supported`**(카탈로그에서 시·도와 구·군이 둘 다 잡히면 `true`)를 실었다. `city`·`district` 코드를 내보내지 않은 이유는 **클라이언트가 판단할 것이 "고를 수 있는가" 하나**였기 때문이다.

> 이 필드는 [ADR-0046](./0046-administrative-region-as-catalog-data.md)으로 **사라졌다** — 카탈로그가 모르는 지역도 등록받기로 하면서 "고를 수 없는 후보"라는 개념 자체가 없어졌다.

판정은 등록이 쓰는 것과 **같은 코드**(`ListingCatalogCodes.findCity`/`findDistrict`)로 한다. 별도 사전을 두면 검색과 등록의 판정이 갈라진다.

### 4. 좌표를 요청과 저장 양쪽에서 필수로 둔다

등록 요청에서 `address.lat`·`address.lng`는 **필수**다(검색을 건너뛴 자유 입력을 막는다). 저장 계약도 함께 조인다 — `ListingValidator`와 MongoDB validator의 `required`에 `location`을 넣는다.

v4 baseline(`0115`)이 `location`을 선택으로 둔 이유는 **채울 경로가 없었기 때문**이지 값이 없어도 되는 필드라서가 아니다. 좌표 없는 매물은 지도·거리 정렬에 오르지 못하고 관리자 승인도 통과하지 못하는 죽은 문서다. 채울 수 있게 된 지금은 그 예외를 유지할 근거가 사라졌다.

**백필 단계가 비어 있다.** migration-policy §4는 확장→백필→축소를 요구하는데, 시드 주입 전이라 좌표 없이 저장된 문서가 0건이라 가운데가 자동으로 충족된다. 조이기는 `0115`를 고치지 않고 **새 changeUnit `0116 listing-location-required`** 가 자기 스키마 사본을 들고 수행한다(baseline은 동결이다). `schemaVersion`은 4 그대로다 — 문서 모양이 바뀌지 않고 이미 유효하던 필드가 필수가 될 뿐이다.

`required`에서 계속 빠지는 셋은 성격이 다르다 — `blogUrl`(선택 입력)·`rejectionReason`(반려 시에만)·`serviceFeedback`(선택 설문)은 값이 없는 것이 정상 상태다.

### 5. 포트·어댑터와 설정

`listing/domain/address/AddressSearchClient`(포트) ↔ `listing/infrastructure/external/ncp/NcpGeocodeClient`(어댑터)로 나눈다 — `PlaceSearchClient`·`ListingImageStorage`와 같은 구조다.

NCP 공식 문서가 **호스트를 두 가지로 적고 있다**(Maps 개요는 `maps.apigw.ntruss.com`, Geocoding 레퍼런스 예시는 구 계열 `naveropenapi.apigw.ntruss.com`). 경로(`/map-geocode/v2/geocode`)와 헤더(`x-ncp-apigw-api-key-id`·`x-ncp-apigw-api-key`)는 같다. 그래서 base URL을 상수가 아니라 **설정값**(`app.naver.geocode.base-url`)으로 두고 기본값을 개요 기준으로 잡는다 — 발급 키가 구 도메인만 받으면 **설정 한 줄**로 바꾼다.

자격증명이 없어도 앱은 기동하고 **주소 검색 호출만 502**다(지역 검색·SMS·사업자번호 검증과 같은 정책).

### 6. 주소 문자열은 손대지 않는다

NCP의 `roadAddress`에는 건물명이 붙어 올 수 있다(`… 불정로 6 NAVER그린팩토리`). **자르지 않는다** — 사용자가 고른 표준 주소이고, `City`·`District` 파싱은 앞부분만 보므로 영향이 없다. 서버가 임의로 다듬으면 "검색 결과 그대로"라는 계약이 깨진다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. NCP Geocoding + 좌표 되돌려 받기(채택)** | 표준 주소·좌표를 한 번에 얻고, 등록 경로에 외부 의존이 늘지 않는다 | 좌표를 클라이언트가 들고 있어 위조가 가능하다 | **채택** |
| B. Naver Developers 지역 검색 재사용 | 이미 연동돼 있어 추가 작업이 거의 없다 | 업체명 검색에 가까워 도로명 주소 자체를 찾기 어렵고, 좌표 정확도가 건물 단위가 아니다 | 미채택 — **이슈 #223이 이미 기각**했다 |
| C. 등록 시 서버가 재지오코딩 | 좌표를 서버가 확정해 위조가 불가능하다 | 등록마다 외부 왕복·실패(502) 경로가 늘고, 검색을 이미 부른 뒤라 중복이다 | 미채택 — 위조는 승인 심사가 흡수한다(§2) |
| D. 프론트가 NCP를 직접 호출 | 서버가 외부 연동을 들지 않는다 | 클라이언트에 키가 박히고 쿼터·호출을 통제할 수 없다 | 미채택 — 사진 직접 업로드를 기각한 것과 같은 이유(ADR-0041 D) |
| E. 행안부 도로명주소 API | 주소 정본이고 무료다 | **좌표를 주지 않는다** — 좌표 API를 또 붙여야 해 연동이 둘이 된다 | 미채택 |
| F. 서버가 지원 지역만 검색 결과로 남긴다 | 폼이 고민할 게 없다 | 부산 주소를 검색한 임대인이 이유 없이 "결과 없음"을 본다 | 미채택 — `supported`로 **이유를 보이게** 한다(§3) |

## Consequences

- **긍정**: 등록 매물이 좌표를 갖는다 — 지도·거리 정렬·관리자 승인 조건이 자동으로 충족된다. 주소 표기가 표준화돼 파싱 실패가 줄고, 남는 실패는 "지원하지 않는 지역"으로 좁혀진다(→ 그 지역도 받기로 하면서 `supported`와 `LISTING_INVALID_ADDRESS`는 [ADR-0046](./0046-administrative-region-as-catalog-data.md)으로 사라졌다). 인프라는 기존 시크릿 주입 경로를 그대로 쓴다.
- **부정/트레이드오프**
  - **등록 요청 계약이 깨진다.** `address.lat`·`lng` 필수화는 기존 요청을 `400`으로 만든다 — 프론트 배포 순서 합의가 필요하다.
  - **외부 의존이 하나 는다.** NCP가 죽으면 주소를 새로 검색할 수 없어 **등록을 시작할 수 없다**(진행 중인 폼은 이미 받은 좌표로 제출할 수 있다).
  - **좌표를 신뢰할 수 없다.** 승인 심사 전까지는 주소와 좌표가 어긋난 문서가 존재할 수 있다.
  - **지오코딩은 부분 키워드에 약하다.** "신촌"으로는 결과가 없고 "신촌로 12"가 필요하다 — 폼의 입력 안내가 결과 품질을 좌우한다.
  - **건물명이 붙은 주소가 저장된다.** 상세 화면의 주소 표시가 길어질 수 있다.
  - 자격증명이 **콘솔별로 둘**이 된다(Developers·NCP). 이름이 비슷해 섞어 넣으면 401·403이 난다.
- **후속 작업**
  - 좌표로 `nearbyUniversityCodes`를 계산한다(ADR-0039가 남긴 나머지 절반).
  - 지원 지역 확장 — `DISTRICT` 카탈로그와 `District` enum을 함께 늘린다(둘 중 하나만 늘리면 조용히 무시된다).
  - 관리자 승인에서 주소·좌표 일치 확인 절차.

## Validation

- 주소 검색에 `신촌로 12`를 보내면 `roadAddress`·`lat`·`lng`가 오고, 그 좌표를 지도에 찍으면 실제 위치와 맞는다.
- 서울 9개 구 밖의 주소(예: `분당구 불정로 6`)도 결과에 포함되며 그대로 등록할 수 있다 — 행정구역이 `ETC`로 저장된다([ADR-0046](./0046-administrative-region-as-catalog-data.md)으로 개정. 본 ADR 시점에는 `supported=false`로 표시하고 등록에서 거절했다).
- 검색 결과의 주소·좌표로 등록하면 `201`이고, 저장된 문서의 `location`이 `{ type: "Point", coordinates: [lng, lat] }`다.
- 좌표를 빼고 등록하면 `400 INVALID_INPUT`이고 `errors[]`에 `address.lat`·`address.lng`가 실린다. 좌표 없는 문서를 강제로 넣으려 해도 도메인 검증과 MongoDB validator(`0116` 적용 후)가 각각 막는다 — `mongosh`에서 `db.listings.insertOne({...location 없이...})`가 `DocumentValidationFailure`다.
- 자격증명을 비우면 주소 검색만 `502 UPSTREAM_ERROR`이고 다른 기능은 정상이다. 이때 **NCP로 나가는 요청 자체가 없다.**
- 세입자(`userType=TENANT`) 토큰으로 부르면 `403 FORBIDDEN`, 토큰 없이 부르면 `401 UNAUTHENTICATED`다(공개 매처에 걸려 200이 나오지 않는다).
