# ADR-0041. 매물 등록을 multipart로 받아 이미지를 S3에 저장한다

| 항목 | 값 |
|---|---|
| 번호 | ADR-0041 |
| 작성자 | Kohere Backend 팀 |
| 작성일 | 2026-08-14 |
| 기준 코드 | `feature/220-listing-registration-api` @ `413478a`. 본 ADR의 파일·경로 참조는 전부 이 시점 기준이며, 재검증 없이 인용하지 않는다 |
| 관련 문서 | [ADR-0020](./0020-terraform-remote-state-s3-dynamodb.md), [ADR-0023](./0023-secrets-in-ssm-parameter-store.md), [ADR-0033](./0033-business-registry-verification.md), [ADR-0039](./0039-listing-schema-v4-registration-form.md), [ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md), [listing API](../api/specs/03-listings-favorites.md) |

## Status

Proposed

## Context

- 이슈 #220의 인수 조건은 "임대인이 매물을 등록할 때 **필요한 정보/이미지를 입력하면 DB와 S3에 매물이 저장된다**"인데, 현재 `POST /api/v2/listings`는 이미지를 **URL 문자열**(`imageUrls`·`roomOffers[].roomImageUrls`)로 받는다. 그 URL을 만들어 주는 주체가 없어 인수 조건이 채워지지 않는다.
- 이슈는 **프론트가 S3에 직접 올리는 방식을 이미 기각**했다 — "S3가 listingId로 구분되어 있으므로 매물 정보를 등록하기 전에는 listingId를 알 수 없다. 매물 정보를 따로, 이미지를 따로 등록할 경우에는 정합성 불일치 상태가 발생할 수 있다."
- **인프라는 이미 있다.** `infra/terraform/modules/shared/s3-cloudfront`가 비공개 S3 버킷 + CloudFront(OAC)를 정의하고, dev·prod IAM이 앱 역할에 `PutObject`·`GetObject`·`DeleteObject`·`ListBucket`을 부여하며(`dev/main.tf:84`·`prod/main.tf:157`), 앱 환경변수 `APP_IMAGES_BUCKET`·`APP_IMAGES_CDN_DOMAIN`까지 주입된다. 모듈 주석이 설계를 못 박아 두었다 — "도메인은 **키 프리픽스로 구분**", "읽기는 CloudFront 직결, **앱은 URL만 저장**".
- 같은 버킷을 **생활팁 이미지**가 `life-tips/` prefix로 이미 쓰고 있고(시드 URL이 `https://cdn.kohere.app/life-tips/…`), 매물 시드도 같은 호스트를 가리킨다. 국기는 이 버킷이 아니라 외부 `flagcdn.com`을 쓴다.
- **앱 쪽은 비어 있다.** AWS SDK 의존성도, `app.images.*` 설정도 없다. 주입되는 환경변수를 읽는 코드가 없다.
- 두 저장소(S3·MongoDB)에 걸친 쓰기라 **분산 트랜잭션이 없다.**

## Decision

**등록 API를 `multipart/form-data`로 받아 서버가 S3에 올리고, 저장한 CloudFront URL을 매물 문서에 넣는다.**

### 1. 요청 형식 — multipart 단일 요청

| part | 내용 | 개수 |
|---|---|---|
| `request` | 등록 정보 JSON (`Content-Type: application/json`) | 1 |
| `listingImages` | 매물 대표 사진 | 1~10 |
| `roomImages{i}` | `roomOffers[i]`의 방 사진 — `roomImages0`, `roomImages1` … | 방마다 2~10 |

**파일과 방의 대응은 part 이름의 인덱스로 한다.** 파일명 규칙에 의존하면 클라이언트가 파일명을 바꾸는 순간 조용히 깨진다. `roomImages{i}`의 `i`가 `roomOffers` 범위를 벗어나면 `400`이다.

`imageUrls`·`roomOffers[].roomImageUrls`는 **요청에서 제거한다.** 서버가 업로드 결과로 채우는 값이 된다.

제약은 **장당 10MB**, `image/jpeg` · `image/png` · `image/webp` · `image/heic`다. HEIC는 **저장만 한다** — 서버가 변환하지 않는다.

### 2. 키 규칙 — 기존 버킷을 prefix로 공유한다

```
listings/{listingId}/cover/{uuid}.{ext}
listings/{listingId}/rooms/{roomOfferId}/{uuid}.{ext}
```

버킷을 새로 만들지 않는다. 모듈 설계대로 도메인을 키 prefix로 가른다(생활팁은 `life-tips/`).

**저장하는 URL은 CloudFront 도메인이다** — `https://{APP_IMAGES_CDN_DOMAIN}/{key}`. 버킷이 비공개(OAC)라 S3 URL로는 읽히지 않는다. 쓰기는 `APP_IMAGES_BUCKET`(버킷 이름)으로, 읽기 URL은 `APP_IMAGES_CDN_DOMAIN`으로 — 같은 객체를 두 경로로 가리킨다.

### 3. 실패 처리 — 업로드 먼저, 실패하면 보상 삭제

분산 트랜잭션이 없으므로 **순서를 고르고 무엇이 남을지를 정한다.**

| 순서 | 중간 실패 시 남는 것 |
|---|---|
| 저장 먼저 → 업로드 | **사진 없는 매물**이 DB에 남는다 — 관리자·통계·후속 로직이 모두 그 거짓을 본다 |
| **업로드 먼저 → 저장(채택)** | 참조 없는 파일이 S3에 남는다 — 아무도 그 존재를 모르고 요금만 든다 |

**덜 나쁜 쪽을 고른다.** 저장이 실패하면 방금 올린 객체를 지우는 **보상 삭제**를 돌린다.

보상 삭제가 돌지 못하는 경우(프로세스 종료·S3 장애)에는 고아 객체가 남는데, **감수한다.** 저장 실패와 보상 실패가 겹쳐야 생기고 결과는 참조 없는 파일 몇 개다. 나중에 문제가 되면 `listings/` 키와 MongoDB를 대조하는 정리 배치를 붙인다 — 실제 상태를 비교하므로 살아 있는 사진을 지울 위험이 없다.

**태그(`pending=true`) 기반 라이프사이클 만료는 쓰지 않는다.** 업로드 시 태그를 달고 저장 성공 후 떼는 방식인데, **태그 제거가 실패하면 살아 있는 매물의 사진이 하루 뒤 조용히 사라진다.** 저장이 성공한 뒤라 보상 삭제로 되돌릴 수도 없다. 드물고 무해한 고아를 막으려다 덜 드물고 해로운 데이터 소실을 들이는 거래다.

### 4. 버저닝을 끈다

버킷은 `enable_versioning` 기본값 `true`로 **켜져 있다.** 켜진 상태에서는 `DeleteObject`가 삭제 마커만 남기고 이전 버전이 그대로 과금돼 **보상 삭제가 실제로 지우지 못한다.** 이미지는 덮어쓸 일이 없고 되돌릴 필요도 없으므로 끈다. 같은 버킷을 쓰는 생활팁도 같은 판단이다.

라이프사이클에는 **미완료 멀티파트 정리**(`abort_incomplete_multipart_upload`, 7일)만 둔다. 지금은 10MB 단일 `PutObject`라 발동하지 않지만, 제한을 올리거나 `S3TransferManager`로 바꾸면 조각이 쌓이는데 `ListObjects`에 보이지 않아 아무도 눈치채지 못한다.

### 5. 포트·어댑터

`listing/domain/image/ListingImageStorage`(포트)와 `listing/infrastructure/external/s3/S3ListingImageStorage`(어댑터)로 나눈다 — `PlaceSearchClient`·`BusinessRegistryVerifier`와 같은 구조다(도메인은 포트만 알고 SDK는 인프라에 가둔다).

로컬은 **MinIO**를 쓴다. S3 API 호환이라 **같은 어댑터에 endpoint만 바꿔** 붙인다 — 별도 스텁 구현을 만들지 않는다. 로컬에서도 실제 업로드 경로를 그대로 태우므로 "로컬만 통과"가 생기지 않는다.

## Alternatives

| 대안 | 장점 | 단점 | 채택 안 한 이유 |
|---|---|---|---|
| **A. multipart 단일 요청(채택)** | 한 번의 요청으로 끝나 정합성 창이 가장 좁다. 프론트가 원하는 형태다 | 요청이 크고 서버가 파일 바이트를 다룬다. Swagger가 파일 part를 표현하지 못한다 | **채택** |
| B. presigned URL 2단계 | 서버 대역폭 0. 대용량에 강하다. 임시 prefix를 쓰면 listingId 문제도 풀린다 | 요청이 2단계로 늘고 고아 정리가 별도로 필요하다 | 미채택 — 프론트가 multipart를 요구한다 |
| C. 등록 후 별도 이미지 API | 각 요청이 단순하다 | 왕복이 늘고 A안의 취지(한 번에 끝낸다)에서 멀어진다 | 미채택 — 다만 등록 직후 `status=PENDING`이라 세입자에게 안 보이므로, 이슈가 걱정한 정합성 위험은 실제로는 작다 |
| D. 프론트가 S3에 직접 전송 | 서버가 파일을 다루지 않는다 | 등록 전에는 listingId가 없어 키를 정할 수 없다 | 미채택 — **이슈가 이미 기각**했다 |

## Consequences

- **긍정**: 인수 조건이 채워진다. 이미 깔려 있던 버킷·CDN·IAM·환경변수를 그대로 쓰므로 인프라 추가가 거의 없다. 로컬도 MinIO로 실제 코드 경로를 검증한다.
- **부정/트레이드오프**
  - **등록 API의 요청 형식이 하위 호환 불가로 바뀐다**(JSON → multipart). 다만 `POST /api/v2/listings`는 아직 배포 전이라 버전을 또 올리지 않는다 — [ADR-0040](./0040-listing-query-api-v2-and-v1-sunset.md)이 닫은 "하위 호환이 깨지면 버전을 올린다" 규약은 **이미 나간 계약**에 대한 것이다.
  - **Swagger에 파일 part가 나오지 않는다.** `restdocs-api-spec` 0.19.4에 multipart 처리가 없어 `requestFields`는 JSON 본문을 전제한다. `request` part의 필드만 문서화하고 파일 part 규칙은 description에 적는다. Swagger UI에서 파일을 붙여 Try it out 할 수 없다.
  - **서버가 파일 바이트를 다룬다.** 요청당 최대 크기가 커져 `spring.servlet.multipart.*` 한도와 컨테이너 메모리를 함께 봐야 한다.
  - **고아 객체를 완전히 막지 못한다.** 보상 삭제가 못 도는 경우를 감수한다.
  - **버저닝을 끄면 실수로 덮어쓴 이미지를 되돌릴 수 없다.** 생활팁 이미지에도 적용된다.
- **후속 작업**
  - HEIC 변환이 필요해지면 별도로 다룬다(지금은 저장만).
  - 고아가 실제로 쌓이면 `listings/` 키와 MongoDB를 대조하는 정리 배치를 만든다.
  - S3가 한 번 켠 버저닝은 `Suspended`만 되고 **기존 버전은 남는다** — 이미 배포된 환경은 일회성 정리가 필요하다.

## Validation

- 등록 요청에 파일을 실어 보내면 응답 `imageUrls`·`roomOffers[].roomImageUrls`가 **`https://{cdn}/listings/{listingId}/…` 형태**이고, 그 키로 버킷에 객체가 실제로 존재한다.
- `roomImages{i}`의 `i`가 `roomOffers` 범위를 벗어나면 `400`이고 **S3에 아무것도 올라가지 않는다**(검증이 업로드보다 앞선다).
- 저장이 실패하면 그 요청에서 올린 객체가 **남지 않는다** — 보상 삭제를 목으로 검증한다.
- 로컬에서 MinIO를 띄우고 `http/listing.http`로 등록하면 버킷에 객체가 생긴다.
