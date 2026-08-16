# ADR-0045. 인근 대학은 시드된 좌표 원장으로 매핑한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0045 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-15 |
| 기준 코드 | `feature/227-listing-university-auto-mapping` @ `0bd8ea8`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0028](./0028-diagnosis-questions-catalog-store.md), [ADR-0032](./0032-mongodb-migration-runner.md), [ADR-0037](./0037-listing-localization-and-code-catalog.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [ADR-0042](./0042-road-address-search-with-ncp-geocoding.md), [ADR-0044](./0044-nearby-station-search-with-kakao-local.md), [listing API](../api/specs/03-listings-favorites.md), [migration-policy §8-1](../database/migration-policy.md#8-1-시드-주입-절차), [database-design §4-3](../database/database-design.md) |

## Status

Proposed

## Context

**등록되는 매물이 진단 추천에서 통째로 빠진다.** 매물의 `nearbyUniversityCodes`는 진단 ③ 대학 그룹과 매칭되는 조인 키인데([ADR-0028](./0028-diagnosis-questions-catalog-store.md)), 등록 API가 이 값을 **빈 배열로 고정**해 저장한다([ListingRegisterService](../../src/main/java/com/kohere/listing/application/ListingRegisterService.java)). 추천 조회는 `nearbyUniversityCodes $in {멤버 코드}`로 거르므로([ListingRepositoryImpl](../../src/main/java/com/kohere/listing/infrastructure/persistence/ListingRepositoryImpl.java)), 빈 배열 매물은 유학 목적 진단의 결과에 **영원히 나오지 않는다**. 임대인이 폼을 다 채워도 그렇다.

값을 채울 재료는 이미 있다. [ADR-0042](./0042-road-address-search-with-ncp-geocoding.md)로 등록이 주소 검색이 준 좌표를 받아 `location`에 저장하고, [`0116`](../../src/main/java/com/kohere/listing/infrastructure/migration/ListingLocationRequiredChangeUnit.java)이 그 좌표를 저장 계약의 **필수**로 조였다. 남은 일은 좌표에서 대학을 고르는 것이다.

**[ADR-0044](./0044-nearby-station-search-with-kakao-local.md)가 이 일을 한 번 시도했다가 뺐다.** 인근 역과 같은 카카오 로컬 연동으로 대학도 훑으려 했으나 — 카카오에 대학 전용 카테고리가 없어 `SC4`(학교)를 `category_name`으로 걸러야 하고, 그 뒤 **장소 이름을 카탈로그 코드로 되돌리는 규칙**이 서지 않았다. `contains`면 `고려대학교사범대학부속고등학교`가 `KOREA`가 되고, `equals`면 `연세대학교 신촌캠퍼스 제1공학관` 같은 캠퍼스 문서가 통째로 빠진다. ADR-0044는 "실제 응답 표기를 실측하기 전에는 규칙을 정할 수 없다"며 별도 이슈로 넘겼다 — 본 ADR이 그 후속이다.

제약 두 가지가 선택지를 좁힌다.

- **코드가 닫힌 집합이다.** 매물이 가질 수 있는 대학 코드는 `listingCatalog`의 `UNIVERSITY` 카테고리 **14개**뿐이고([ADR-0037](./0037-listing-localization-and-code-catalog.md)), 진단의 `UniversityGroup` 멤버 코드와 1:1이다. 외부 제공자가 아는 전국 수천 개 대학은 애초에 대상이 아니다.
- **`listingCatalog`는 번역 사전이다.** 좌표는 표시 문자열이 아니라 지오 데이터이고, 카탈로그는 `(category, code) → label` 한 가지 일만 한다. 좌표를 그 문서에 끼워 넣으면 19개 카테고리 중 하나만 모양이 다른 컬렉션이 된다.

## Decision

**서버가 대학 좌표 원장 컬렉션 `universities`를 갖고, 매물 등록이 저장 좌표에서 반경 2km 안의 대학 코드를 파생해 `nearbyUniversityCodes`에 채운다.** 세부는 다음과 같다.

### 1. 원장은 새 컬렉션 `universities` — 좌표만 담는다

| 필드 | 타입 | 비고 |
|---|---|---|
| `_id` | string | 코드값 그대로(`SNU`). 재시드가 같은 문서를 덮어쓴다 |
| `code` | string | `listingCatalog`·매물 `nearbyUniversityCodes`와 같은 값(조인 키) |
| `location` | GeoJSON `Point` | 캠퍼스 대표 좌표 `[경도, 위도]` · **required** |

**라벨을 두지 않는다.** 번역 정본은 `listingCatalog`의 `UNIVERSITY` 카테고리이고 두 컬렉션은 `code`로 조인한다 — 같은 라벨을 두 곳에 두면 한쪽만 고쳐지는 날이 온다. 반대로 좌표를 `listingCatalog`에 넣지 않는 이유는 §Context의 두 번째 제약이다.

`location`이 `required`인 이유는 좌표 없는 대학 문서가 **반경 조회에서 영영 잡히지 않는 죽은 행**이기 때문이다. 넣는 순간 validator가 막는다.

### 2. 외부 조회가 아니라 시드 원장이다

등록 때마다 카카오·네이버에 묻지 않는다. 원장을 서버가 가지면 ADR-0044를 막았던 **이름→코드 매칭이 사라진다** — 원장은 코드 자체를 키로 들고 있다. 덤으로 등록 경로에 외부 왕복·502 분기·쿼터가 늘지 않는다([ADR-0042](./0042-road-address-search-with-ncp-geocoding.md) §2가 등록 시점 재지오코딩을 뺀 것과 같은 이유다).

**좌표의 출처는 교육부 학교개황(20241007 기준)의 도로명 주소를 NCP Geocoding으로 변환한 값**이다. 조인은 학교명이 아니라 학교개황의 **학교코드**로 한다 — 이름 매칭이 위험한 것은 카카오 응답이든 통계 원장이든 같다. 14개 모두 서울 소재 **본교** 행이며, 생성 스크립트는 저장소에 두지 않는다(1회성, 결과물인 시드 JSON만 남는다).

### 3. 반경 2km · 해당하는 대학 전부

`$geoWithin`+`$centerSphere`로 반경 안의 문서를 모두 읽는다. `$nearSphere`를 쓰지 않는 이유는 거리순 정렬이 필요 없고(반환값은 집합), `$nearSphere`는 2dsphere 인덱스가 없으면 조회 자체가 실패하는 반면 `$geoWithin`은 인덱스 없이도 답을 내기 때문이다. 인덱스는 성능으로만 기여한다.

**2km인 이유**: 도보~버스 한두 정거장 생활권이다. 더 넓히면 서울 도심 밀도상 무관한 대학까지 붙고, 좁히면 대학가 매물이 빈 배열로 남아 §Context의 문제가 그대로 재발한다.

**최근접 1개가 아니라 전부인 이유**: 신촌 일대는 연세·이화·홍익이 서로 2km 안이고, 진단의 `HONGIK_YONSEI_EWHA` 그룹이 정확히 그 셋을 묶는다([ADR-0028](./0028-diagnosis-questions-catalog-store.md)). 하나만 고르면 같은 그룹을 고른 사용자에게 매물이 덜 보인다.

### 4. 파생 시점은 등록 1회 · 실패해도 등록을 막지 않는다

값은 등록 시점 원장 기준이며, 원장이 바뀌어도 기존 매물을 소급 갱신하지 않는다(등록 좌표가 바뀌지 않는 한 결과도 같다).

조회 결과가 비어도 **등록은 성공한다.** 대학가 밖 매물은 정상적으로 빈 집합이고, 원장이 비어 있어도(시드 전 신규 환경) 같은 결과라 둘을 구분할 수 없다 — 등록을 세우면 정상 매물이 막힌다. 대신 경고 로그를 남긴다. 이것이 배포 절차에서 시드 단계가 빠졌다는 유일한 신호다.

**등록 폼은 대학을 묻지 않는다.** 임대인이 고르게 하면 자기 매물을 띄우려고 먼 대학까지 넣는다 — 요청 계약은 그대로 두고 서버가 파생한다.

### 5. 마이그레이션은 스키마만, 시드는 정본 JSON 주입

`0118 listing-university-collection`이 `$jsonSchema` validator를 적용한다(없으면 `createCollection`, 있으면 `collMod`). **문서는 넣지 않는다.** 지오 인덱스(`universities_location_2dsphere`)는 부트스트랩([`ListingMongoIndexInitializer`](../../src/main/java/com/kohere/listing/infrastructure/persistence/ListingMongoIndexInitializer.java))이 멱등 생성한다.

이는 [ADR-0032](./0032-mongodb-migration-runner.md) §4의 규칙 그대로다 — changeUnit은 스키마·문서 이행만 맡고, 레퍼런스 데이터는 운영자가 주입한다. 대학 좌표는 **캠퍼스 이전·코드 추가가 서비스 운영 중에 생기는 값**이라 특히 그렇다: 좌표 한 줄 고치는 데 재빌드·재배포가 필요하면 원장이 낡은 채로 방치된다.

정본 시드 JSON은 테스트가 읽는 파일과 **같은 파일**이다([`universities.json`](../../src/test/resources/fixtures/universities.json)) — 운영과 테스트가 서로 다른 좌표를 보지 않게 한다. 주입 절차는 [migration-policy §8-1](../database/migration-policy.md#8-1-시드-주입-절차)이며 `--drop` 없이 `deleteMany({})` 후 import한다(컬렉션을 지우면 validator가 함께 사라지고 `0118`은 1회성이라 복구되지 않는다).

### 6. 기존 매물 백필은 하지 않는다

시드 매물 2건은 `nearbyUniversityCodes`를 이미 들고 있고, 등록 API로 저장된 실데이터가 아직 없다 — 백필 changeUnit을 쓸 대상이 0건이다. 운영 데이터가 쌓인 뒤에 원장이 바뀌면 그때 별도 changeUnit으로 다룬다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. 시드 좌표 원장 + 반경 조회 (채택)** | 이름 매칭 불필요, 외부 의존·쿼터 없음, 결정적(같은 좌표→같은 결과), 테스트 가능 | 원장을 사람이 관리(캠퍼스 이전 시 갱신) | — (채택) |
| **B. 카카오 로컬 `SC4` 실시간 조회** | 원장 관리 불필요 | **장소 이름→코드 규칙이 서지 않는다**(부속고교 오탐 / 캠퍼스 건물 누락), 등록마다 외부 왕복·502 경로·쿼터 | [ADR-0044](./0044-nearby-station-search-with-kakao-local.md)가 바로 이 이유로 뺐고, 그 사정이 그대로다 |
| **C. `listingCatalog`에 좌표 컬럼 추가** | 새 컬렉션 없음 | 번역 사전이 지오 데이터를 겸함, 19개 카테고리 중 하나만 모양이 다름, `UNIVERSITY` 외 카테고리엔 무의미한 필드 | 카탈로그의 단일 책임(코드→라벨)을 깬다 |
| **D. 임대인이 등록 폼에서 직접 선택** | 구현 최소 | 노출을 노린 과다 선택, 검증 불가(좌표와 대조하면 결국 A가 필요) | 데이터 신뢰도가 진단 추천 품질을 직접 깎는다 |
| **E. 행정구역(구) 기준 매핑** | 좌표 불필요 | 구 경계와 생활권이 다르다(마포구 매물이 연세대와 1km인데 서대문구라 빠진다) | 좌표가 이미 필수인데 더 거친 근사를 쓸 이유가 없다 |

## Consequences

- **긍정**
  - 등록된 매물이 진단 추천 대상에 들어온다(본 ADR의 동기 해소).
  - 등록 경로에 외부 호출이 늘지 않는다 — 실패 분기·타임아웃·쿼터가 그대로다.
  - 결정적이라 테스트가 쉽다. 같은 좌표는 언제나 같은 코드 집합을 준다.
  - ADR-0044가 열어 둔 숙제가 닫힌다 — 카카오 대학 조회는 포트에서 영구히 빠진다.
- **부정/트레이드오프**
  - **원장을 사람이 관리한다.** 캠퍼스 이전·신규 코드는 시드 갱신이 필요하고, 갱신을 잊으면 조용히 옛 좌표로 매핑된다.
  - **반경 2km는 매직 넘버다.** 경계에 걸린 대학(신촌 기준 이화여대 ~2.1km)은 붙거나 빠지는 것이 수십 미터로 갈린다.
  - **캠퍼스를 점 하나로 본다.** 서울대 관악처럼 넓은 캠퍼스는 정문 좌표 기준이라 후문 쪽 매물의 실제 거리와 어긋난다.
  - 신규 환경은 시드 전까지 모든 등록이 빈 배열로 저장된다 — 경고 로그와 배포 절차의 시드 단계로 막는다.
- **후속 작업**
  - 매물 수정 API가 생기면 좌표 변경 시 재파생 지점이 하나 더 생긴다(현재 수정 API가 없어 범위 밖).
  - 운영 데이터가 쌓인 뒤 원장이 바뀌면 백필 changeUnit을 별도로 만든다.
  - 매물이 서울 밖으로 넓어지면 원장 14건과 반경 2km를 함께 재검토한다.

## Validation

- **반경 경계**: 서울대 남쪽 1.9km는 잡히고 2.1km는 빠진다(통합 테스트).
- **다중 매핑**: 신촌 좌표가 `YONSEI`·`EWHA`·`HONGIK` 셋을 모두 반환한다 — 진단 그룹과 맞물리는지 확인.
- **빈 원장**: 시드 전 조회가 예외 없이 빈 집합을 준다 — 등록이 멈추지 않는다.
- **등록 계약**: `POST /api/v2/listings` 응답의 `nearbyUniversityCodes`가 좌표에서 파생된 코드로 채워진다(REST Docs 스니펫).
- **인덱스**: 기동 시 `universities_location_2dsphere`가 멱등 생성된다.
- **모듈 경계**: 새 포트·어댑터가 전부 `listing` 안이라 `ApplicationModules.verify()`([ModularityTest](../../src/test/java/com/kohere/ModularityTest.java))가 green이다.
- **재검토 시점**: 대학 코드가 14개를 크게 넘거나(전국 확장) 캠퍼스 단위 매핑이 필요해지면 원장 구조(캠퍼스별 문서·폴리곤)와 반경을 다시 본다.
