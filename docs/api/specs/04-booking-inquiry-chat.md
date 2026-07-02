# 매물 예약(신청) · (후속) 문의 · 인앱 채팅 API Spec

> [api-design-guide](../api-design-guide.md) · [error-response-guide](../error-response-guide.md)를 따른다. 모든 응답은 공통 래퍼.
> 관련 유저 스토리: [user-stories](../../requirements/user-stories.md)

## 개요

세입자(외국인 사용자)가 매물의 방 상품(`roomOffer`)에 **예약(= 신청, Booking)** 을 신청하고, 자신의 예약 내역을 목록·단건 상세로 다시 확인한다. **본 서비스에서 "신청"과 "예약"은 같은 `Booking`을 가리키는 동의어다.**

- **[1차 MVP] 매물 예약(신청)**: 세입자가 방 상품에 타겟 입주일 + 계약기간(개월수)으로 예약을 생성·저장하고, 내 예약을 목록·단건 상세로 조회한다. 인앱 채팅과 **분리된 독립 기능**이다. MVP의 예약은 "신청" 성격이라 중복 신청 제한·본인 매물 차단이 없다(예약은 세입자 전용).
- **[후속·이연] 문의 · 인앱 채팅**: 임대인과의 1:1 채팅방 문의·리스트·메시지·읽음 처리, 그리고 예약 신청 시 채팅방에 예약 카드를 자동 기록하던 결합(F-03 chat)은 1차 MVP에서 후속으로 분리·이연한다(삭제 아님 — 설계 보존).

문서 구조:

- **[1차 MVP] 매물 예약(신청)** — 엔드포인트 요약 · 1. 예약 생성 · 2. 예약 목록 · 3. 예약 상세
- **[후속·이연] 문의 · 인앱 채팅** — 4. 문의 · 5. 채팅방 리스트 · 6. 메시지 조회 · 7. 메시지 전송 · 8. 읽음 처리

### 핵심 개념·enum

| 개념 | 값 | 설명 |
| --- | --- | --- |
| 계약기간 `contractPeriod` | 정수(개월수, 예: `1`·`3`·`6`·`12`·`24` …) | 예약(신청) 시 입력하는 계약 기간(개월 단위 양의 정수). 총 금액 계산에 개월수로 그대로 쓴다 |
| 예약 상태 `status` (Booking) | `REQUESTED`, `ACCEPTED`, `REJECTED`, `CANCELED` | 신청 직후 `REQUESTED` 고정. 임대인 수락/거절·세입자 취소 등 상태 전이는 본 스펙 범위 밖(확장 시 정의) |
| 채팅방 카테고리 `category` (후속·이연) | `LANDLORD`, `NEIGHBOR` | 문의로 생성되는 방은 모두 `LANDLORD`. `NEIGHBOR`(이웃 채팅)는 다른 기능에서 생성되며 본 스펙은 리스트 조회의 필터 값으로만 노출한다 |
| 메시지 타입 `type` (후속·이연) | `TEXT`, `BOOKING_CARD`, `LISTING_CARD`, `SYSTEM` | `BOOKING_CARD`/`LISTING_CARD`는 서버가 생성하는 고정 카드. 사용자 전송은 `TEXT`만 허용 |
| 참여자 역할 (후속·이연) | 세입자(요청자) / 임대인(매물 소유자) | 방은 (매물, 세입자, 임대인) 조합으로 유일 |

- 날짜만 표기는 `YYYY-MM-DD`(예: `moveInDate`), 시각은 ISO-8601 UTC(예: `2026-06-15T08:30:00Z`).
- 금액은 KRW 정수(예: `monthlyRent: 500000`).
- `listingId`·`roomOfferId`는 MongoDB ObjectId의 24자리 hex 문자열이다. `bookingId`는 booking 모듈 저장소의 숫자 식별자(`Long`)다. (후속·이연) `chatRoomId`·`messageId`는 각 모듈 저장소의 숫자 식별자를 유지한다.
- **총 금액**은 예약 상세에서만 계산해 내려준다: `totalAmount = deposit + monthlyRent × 계약 개월수`(관리비 `maintenanceFee`는 제외). 아래 [3. 예약 상세](#3-get-apiv1bookingsbookingid--예약-단건-상세) 참조.

### 저장·조합 규약 (매물 예약)

- **Booking 저장 필드**: `id`(bookingId, `Long`, PK) · `tenantId`(`Long`) · `listingId`(string) · `roomOfferId`(string) · `moveInDate`(`LocalDate`) · `contractPeriod`(정수, 개월수) · `status`(enum, 생성 시 `REQUESTED` 고정) · `createdAt`(`Instant`). 예약은 append 성격(중복 제한·유니크 제약 없음)이며, 숫자 PK·조회 정합상 저장소는 **MySQL 유력**이나 [ADR-0005](../../adr/0005-polyglot-persistence.md) 폴리글랏 매핑 표에서 `booking`은 아직 "추후 결정"이라 **(확인 필요)** 로 둔다.
- **중복 방지**: 동일 세입자–동일 방 상품의 활성 예약(`REQUESTED`/`ACCEPTED`)은 `(tenantId, roomOfferId)` 유니크 제약 + 트랜잭션으로 **정확히 1건**만 허용한다.
- **스냅샷 없음 — 조회 시점 실시간 조인**: 가격·매물 요약·예약자 성명은 예약에 스냅샷 저장하지 않고, 조회 시점에 애플리케이션 레벨로 조합한다. `listing :: api`로 `(listingId, roomOfferId)`의 매물 요약·`pricing`(보증금·월세)을, `user :: api`(`getUserName`)로 예약자 성명을 조회한다(둘 다 신규 공개 조회 메서드 필요). cross-store 조인·트랜잭션은 금지된다([ADR-0005](../../adr/0005-polyglot-persistence.md), [ADR-0002](../../adr/0002-inter-module-communication-via-events.md)). 가격 변경 시 상세는 **현재가 기준**으로 계산한다.
- **모듈 의존**: `booking → { listing::api, user::api }` — `booking/package-info.java` 의존 화이트리스트에 추가가 선행돼야 한다.
- **인증·상태 게이트**: 온보딩을 마친 `ACTIVE` 세입자(`userType=TENANT`) 전용. 다른 보호 엔드포인트와 **동일한 온보딩 상태 게이트**로 검사한다.

---

## [1차 MVP] 매물 예약(신청)

### 엔드포인트 요약(1차 MVP)

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/listings/{listingId}/bookings` | 매물 예약(신청) 생성·저장 | 필수 | 201 |
| GET | `/api/v1/bookings` | 내 예약 목록(요청자 본인, 오프셋 페이지네이션) | 필수 | 200 |
| GET | `/api/v1/bookings/{bookingId}` | 내 예약 단건 상세(본인만) | 필수 | 200 |

> 예약 생성은 매물의 방 상품에 종속되는 액션이므로 `/listings/{listingId}` 하위 1단계 중첩으로 둔다(api-design-guide §2). 조회는 예약을 독립 컬렉션(`/bookings`)으로 둔다.

---

### 1. POST `/api/v1/listings/{listingId}/bookings` — 매물 예약(신청) 생성

방 상품(`roomOffer`)에 타겟 입주일과 계약기간(개월수)으로 예약을 생성·저장한다. 신청 직후 상태는 `REQUESTED` 고정이다. MVP의 예약은 "신청" 성격이라 **중복 제한이 없다** — 같은 방 상품에도 여러 번 신청할 수 있다.

- **인증**: 필수. 요청자는 `ACTIVE` 상태의 세입자(`userType=TENANT`)여야 한다. **예약은 세입자 전용** — 임대인(매물 소유자)은 예약할 수 없으며(비세입자 `403 FORBIDDEN`), 세입자가 자기 소유 매물을 예약하는 상황 자체가 성립하지 않으므로 본인 매물 차단은 두지 않는다.
- 매물·방 상품 존재·공개 여부는 `listing :: api`로 검증한다(소유자 조회 불요; cross-store 조인 금지, ADR-0005).

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `listingId` | string | 필수 | 예약 대상 매물 ID(ObjectId hex 문자열) |

#### Request Body

```json
{
  "roomOfferId": "6858e2000000000000000abc",
  "moveInDate": "2026-07-01",
  "contractPeriod": 6
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `roomOfferId` | string | 필수 | 예약 대상 방 상품 ID(ObjectId hex 문자열). 누락은 `INVALID_INPUT`(400) |
| `moveInDate` | string(`YYYY-MM-DD`) | 필수 | 타겟 입주일. 날짜 형식(`YYYY-MM-DD`) 위반은 `MALFORMED_REQUEST`(400), 형식은 맞으나 과거/입주 가능일 이전이면 `BOOKING_INVALID_MOVE_IN_DATE`(422) |
| `contractPeriod` | integer | 필수 | 계약 개월수(양의 정수, 1 이상). 누락·0·음수는 `INVALID_INPUT`(400), 숫자 아닌 타입은 `MALFORMED_REQUEST`(400) |

#### 성공 Response — 201 Created

`Location: /api/v1/bookings/{bookingId}`

```json
{
  "success": true,
  "data": {
    "bookingId": 9001,
    "status": "REQUESTED",
    "listingId": "6858e2000000000000000001",
    "roomOfferId": "6858e2000000000000000abc",
    "moveInDate": "2026-07-01",
    "contractPeriod": 6,
    "createdAt": "2026-06-15T08:30:00Z"
  },
  "error": null
}
```

> 생성 응답은 예약 코어 내역만 담는다. 매물 요약·가격·예약자 성명은 [3. 예약 상세](#3-get-apiv1bookingsbookingid--예약-단건-상세)에서 조회 시점 조인으로 내려준다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필수값 누락(`roomOfferId`/`contractPeriod`), `contractPeriod`가 양의 정수 아님(0·음수) |
| 400 | `MALFORMED_REQUEST` | JSON 파싱 불가 또는 필드 타입 불일치(예: `moveInDate` 날짜 형식 위반) |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |
| 403 | `AUTH_ONBOARDING_REQUIRED` | 온보딩 미완료(비`ACTIVE`) |
| 403 | `FORBIDDEN` | 세입자(`TENANT`)가 아닌 사용자(임대인)의 예약 시도 |
| 404 | `LISTING_NOT_FOUND` | 매물 또는 방 상품이 없거나 비공개/삭제됨 |
| 422 | `BOOKING_INVALID_MOVE_IN_DATE` | `moveInDate`가 과거이거나 매물의 입주 가능일 이전 |

> 온보딩 미완료(비`ACTIVE`) 사용자는 다른 보호 엔드포인트와 동일한 온보딩 상태 게이트 에러로 차단한다(코드 게이트와 1:1 일치, [error-response-guide](../error-response-guide.md)).

---

### 2. GET `/api/v1/bookings` — 내 예약 목록

요청자 **본인의 예약**만 `createdAt` 내림차순으로 반환한다. **오프셋 페이지네이션**(api-design-guide §4-1). 타인 예약은 조회되지 않는다.

- **인증**: 필수. 본인 예약만 반환된다(타인 예약은 애초에 목록에 없음).

#### Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | int | 선택 | 0 | 0-base 페이지 번호 |
| `size` | int | 선택 | 20 | 페이지 크기(최대 100). 범위 초과는 `INVALID_INPUT`(400) |

> 정렬은 `createdAt,desc` 고정(쿼리로 변경 불가). 각 항목의 매물 요약(제목·썸네일)은 조회 시점에 `listing :: api`로 실시간 조인한다.

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "bookingId": 9001,
        "listing": {
          "listingId": "6858e2000000000000000001",
          "title": "강남역 도보 5분 원룸",
          "thumbnailUrl": "https://cdn.kohere.com/listings/6858e2000000000000000001/thumb.jpg"
        },
        "roomOfferId": "6858e2000000000000000abc",
        "moveInDate": "2026-07-01",
        "contractPeriod": 6,
        "status": "REQUESTED",
        "createdAt": "2026-06-15T08:30:00Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 2,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

> 예약이 하나도 없으면 `content: []` + `page.totalElements: 0` + `page.hasNext: false`(에러 아님).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `size` 범위 초과 등 페이지 파라미터 오류 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |

---

### 3. GET `/api/v1/bookings/{bookingId}` — 예약 단건 상세

요청자 **본인의 예약 1건**을 상세 조회한다. 매물 정보·예약 일시·타겟 입주일·계약기간·예약자 성명·보증금·총 금액을 내려준다. **가격·매물 정보·예약자 성명은 스냅샷이 아니라 조회 시점에 실시간 조인**한다(가격 변경 시 현재가 기준).

- **인증**: 필수. **본인 예약이 아니거나 존재하지 않으면 `404 BOOKING_NOT_FOUND`**(존재 여부를 노출하지 않도록 본인 예약이 아니면 404로 통일).
- **실시간 조인**: `listing :: api`로 `(listingId, roomOfferId)`의 매물 요약·주소·방 상품명·`pricing`(보증금·월세)을, `user :: api`(`getUserName`)로 예약자 성명(`tenantName`)을 조회해 조합한다.
- **총 금액**: `totalAmount = deposit + monthlyRent × contractPeriod`(`contractPeriod`는 계약 개월수 정수). **관리비(`maintenanceFee`)는 총액에서 제외**한다.

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `bookingId` | Long | 필수 | 예약 ID |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "bookingId": 9001,
    "status": "REQUESTED",
    "createdAt": "2026-06-15T08:30:00Z",
    "moveInDate": "2026-07-01",
    "contractPeriod": 6,
    "listing": {
      "listingId": "6858e2000000000000000001",
      "title": "강남역 도보 5분 원룸",
      "thumbnailUrl": "https://cdn.kohere.com/listings/6858e2000000000000000001/thumb.jpg",
      "address": "서울특별시 강남구 역삼동 …",
      "roomOfferId": "6858e2000000000000000abc",
      "roomOfferName": "원룸 A타입"
    },
    "tenantName": "John Doe",
    "deposit": 5000000,
    "totalAmount": 8000000
  },
  "error": null
}
```

> 위 예: `deposit` 5,000,000 + `monthlyRent` 500,000 × `contractPeriod` 6 = `totalAmount` 8,000,000. 관리비는 포함하지 않는다. 예약한 방 상품이 이후 비공개/삭제된 경우 예약 코어 내역(날짜·계약기간·상태)은 유지하되 매물 정보·가격 파트의 표기 정책(매물 필드 `null`/tombstone vs 별도 상태 코드)은 **(확인 필요)**.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |
| 404 | `BOOKING_NOT_FOUND` | 예약이 없거나 **타인의 예약**(본인 예약이 아니면 404로 통일) |
| 404 | `LISTING_NOT_FOUND` | 조인 대상 매물/방 상품이 없음 — 표기 정책은 위 (확인 필요) 참조 |

---

## [후속·이연] 문의 · 인앱 채팅

> **아래 문의(§4)·채팅(§5~§8)은 인앱 채팅(기존 F-03 chat 결합)으로, 1차 MVP에서는 후속으로 분리·이연한다(삭제 아님 — 설계 보존).** 매물 예약(신청, §1~§3)과 달리 문의·채팅 기능, 그리고 **예약 신청 시 채팅방에 예약 카드(`BOOKING_CARD`)를 자동 전송하고 `BookingCreatedEvent`를 발행하던 결합**은 재개 시 구현한다. 대응 유저 스토리는 US-4-3~US-4-5, 시퀀스 다이어그램은 [04-booking-inquiry-chat](../../architecture/sequence-diagrams/04-booking-inquiry-chat/README.md)에 보존돼 있다(재개 시 번호·경로 재정합).
>
> **예약 생성 시 채팅 결합(재개 시)**: 재개하면 예약(신청) 생성과 동시에 임대인과의 채팅방을 보장(없으면 생성)하고, 그 방에 **예약 정보 카드**(`BOOKING_CARD`)를 `pinned: true` 시스템 메시지로 자동 전송하며, 새 메시지에 대해 임대인에게 푸시 알림을 발행하는 `BookingCreatedEvent`를 발행한다. 이때 예약 생성 응답에 `chatRoomId`·`bookingCard`(pinned)가 추가되고 `Location`은 `/api/v1/chat-rooms/{roomId}`가 될 수 있다. 이 결합은 1차 MVP §1(예약 생성)에서는 하지 않는다.

### 엔드포인트 요약(후속·이연)

| Method | Path | 설명 | 인증 | 성공 status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/listings/{listingId}/inquiries` | 매물 문의(채팅방 생성/조회) + 매물 카드 고정 | 필수 | 201 (신규) / 200 (기존 반환) |
| GET | `/api/v1/chat-rooms` | 내 채팅방 리스트 조회(오프셋 페이지네이션) | 필수 | 200 |
| GET | `/api/v1/chat-rooms/{roomId}/messages` | 채팅방 메시지 조회(커서 페이지네이션) | 필수 | 200 |
| POST | `/api/v1/chat-rooms/{roomId}/messages` | 텍스트 메시지 전송 | 필수 | 201 |
| POST | `/api/v1/chat-rooms/{roomId}/read` | 읽음 처리(마지막 읽은 메시지까지 갱신) | 필수 | 200 |

> 문의는 매물에 종속되는 액션이므로 `/listings/{listingId}` 하위 1단계 중첩으로 둔다(api-design-guide §2). 채팅방·메시지는 독립 컬렉션으로 둔다.
> **고정 메시지(pinned)**: 채팅방 상단에 고정되는 카드. `BOOKING_CARD`/`LISTING_CARD`가 `pinned: true`로 내려간다.

---

### 4. (후속·이연) POST `/api/v1/listings/{listingId}/inquiries` — 매물 문의(채팅방 생성/조회)

> **[후속·이연]** 인앱 채팅 결합으로 1차 MVP에서 이연. 대응 유저 스토리 US-4-3.

해당 매물 임대인과의 채팅방을 반환한다. 방이 없으면 새로 만들고 **매물 정보 카드**(`LISTING_CARD`)를 고정한 뒤 `201`을, 이미 있으면 기존 방을 `200`으로 반환한다(멱등적 보장). 신규 생성 시에만 매물 카드 메시지가 추가된다.

- **인증**: 필수. 요청자는 세입자가 된다. 본인이 소유한 매물에는 문의할 수 없다.

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `listingId` | string | 필수 | 문의 대상 매물 ID(ObjectId hex 문자열) |

#### Request Body

본문 없음(빈 본문). 첫 메시지를 함께 보내려면 방 생성 후 메시지 전송 API(아래 7)를 호출한다.

#### 성공 Response — 201 Created (신규 생성) / 200 OK (기존 방 반환)

신규는 `Location: /api/v1/chat-rooms/{roomId}` 헤더를 포함한다.

```json
{
  "success": true,
  "data": {
    "chatRoomId": 556,
    "category": "LANDLORD",
    "created": true,
    "listing": {
      "listingId": "6858e2000000000000000001",
      "title": "강남역 도보 5분 원룸",
      "thumbnailUrl": "https://cdn.kohere.com/listings/6858e2000000000000000001/thumb.jpg",
      "monthlyRent": 500000
    },
    "counterpart": {
      "userId": 42,
      "nickname": "집주인A",
      "profileImageUrl": "https://cdn.kohere.com/users/42/profile.jpg"
    },
    "listingCard": {
      "messageId": 80001,
      "type": "LISTING_CARD",
      "pinned": true,
      "listingId": "6858e2000000000000000001",
      "title": "강남역 도보 5분 원룸",
      "monthlyRent": 500000
    }
  },
  "error": null
}
```

> `created`가 `true`면 신규(201), `false`면 기존 방(200). POST이지만 기존 방 반환은 생성 아닌 액션이므로 `200`을 쓴다(api-design-guide §1). 기존 방 반환 시 `listingCard`는 이미 고정된 카드 정보를 그대로 담는다.

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |
| 404 | `LISTING_NOT_FOUND` | 매물이 없거나 비공개/삭제됨 |
| 422 | `CHAT_SELF_INQUIRY_NOT_ALLOWED` | 본인이 소유한 매물에 문의 |

---

### 5. (후속·이연) GET `/api/v1/chat-rooms` — 내 채팅방 리스트

> **[후속·이연]** 인앱 채팅 결합으로 1차 MVP에서 이연. 대응 유저 스토리 US-4-4.

요청자가 참여한 채팅방 목록을 마지막 메시지 시각 내림차순으로 반환한다. **오프셋 페이지네이션**(api-design-guide §4-1).

- **인증**: 필수. 본인이 참여한 방만 반환된다(타인 방은 애초에 목록에 없음).

#### Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `category` | enum | 선택 | (전체) | `LANDLORD` / `NEIGHBOR`. 미지정 시 전체. 미정의 값은 `INVALID_INPUT`(api-design-guide §5) |
| `page` | int | 선택 | 0 | 0-base 페이지 번호 |
| `size` | int | 선택 | 20 | 페이지 크기(최대 100) |

> 정렬은 `lastMessageAt,desc` 고정(쿼리로 변경 불가).

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "chatRoomId": 555,
        "category": "LANDLORD",
        "listing": {
          "listingId": "6858e2000000000000000001",
          "title": "강남역 도보 5분 원룸",
          "thumbnailUrl": "https://cdn.kohere.com/listings/6858e2000000000000000001/thumb.jpg"
        },
        "counterpart": {
          "userId": 42,
          "nickname": "집주인A",
          "profileImageUrl": "https://cdn.kohere.com/users/42/profile.jpg"
        },
        "lastMessage": {
          "type": "TEXT",
          "preview": "네, 내일 방문 가능합니다.",
          "sentAt": "2026-06-15T09:10:00Z"
        },
        "unreadCount": 3,
        "lastMessageAt": "2026-06-15T09:10:00Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 4,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

> `lastMessage.preview`: `TEXT`는 본문 앞부분, 카드/시스템 메시지는 타입에 대응하는 요약 문구(클라이언트가 `type`으로 다국어 매핑). `unreadCount`는 요청자가 아직 읽지 않은 메시지 수. 참여 중인 방이 없으면 `content: []` + `page.totalElements: 0` + `page.hasNext: false`(에러 아님).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `category` enum 불일치, `size` 범위 초과 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |

---

### 6. (후속·이연) GET `/api/v1/chat-rooms/{roomId}/messages` — 채팅방 메시지 조회

> **[후속·이연]** 인앱 채팅 결합으로 1차 MVP에서 이연. 대응 유저 스토리 US-4-5.

채팅방의 메시지를 최신순으로 **커서 페이지네이션**(api-design-guide §4-2)으로 반환한다. 고정 카드(`pinned: true`)도 메시지 목록에 포함되며 상단 고정 표시는 클라이언트가 `pinned`로 처리한다.

- **인증**: 필수. **본인이 참여하지 않은 방이면 `403 FORBIDDEN`.**

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `roomId` | Long | 필수 | 채팅방 ID |

#### Query 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `cursor` | string | 선택 | (첫 페이지 생략) | `nextCursor` 토큰. 해당 메시지보다 **이전(과거)** 메시지를 조회 |
| `size` | int | 선택 | 30 | 페이지 크기(최대 100) |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "messageId": 70050,
        "type": "TEXT",
        "senderId": 42,
        "mine": false,
        "content": "네, 내일 방문 가능합니다.",
        "pinned": false,
        "sentAt": "2026-06-15T09:10:00Z"
      },
      {
        "messageId": 70001,
        "type": "BOOKING_CARD",
        "senderId": null,
        "mine": false,
        "pinned": true,
        "card": {
          "moveInDate": "2026-07-01",
          "contractPeriod": 6,
          "monthlyRent": 500000,
          "listingTitle": "강남역 도보 5분 원룸"
        },
        "sentAt": "2026-06-15T08:30:00Z"
      }
    ],
    "nextCursor": "70001",
    "hasNext": true
  },
  "error": null
}
```

> 시스템·카드 메시지는 `senderId: null`(서버 생성). `mine`은 요청자가 보낸 메시지면 `true`. `content`는 `TEXT`에만, `card`는 카드 타입에만 존재한다. `BOOKING_CARD`는 재개 시 예약 생성에서 자동 전송되는 고정 카드다(위 이연 배너 참조).

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `size` 범위 초과, `cursor` 형식 오류 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |
| 403 | `FORBIDDEN` | 요청자가 해당 방의 참여자가 아님 |
| 404 | `CHAT_ROOM_NOT_FOUND` | 방이 존재하지 않음 |

---

### 7. (후속·이연) POST `/api/v1/chat-rooms/{roomId}/messages` — 텍스트 메시지 전송

> **[후속·이연]** 인앱 채팅 결합으로 1차 MVP에서 이연. 대응 유저 스토리 US-4-5.

채팅방에 **텍스트 메시지**(`TEXT`)를 보낸다. **이미지·파일 전송은 허용하지 않는다.** 전송 시 방의 `lastMessageAt`을 갱신하고, 상대방에게 푸시 알림 도메인 이벤트를 발행한다.

- **인증**: 필수. **본인이 참여하지 않은 방이면 `403 FORBIDDEN`.**
- 도배 방지를 위해 레이트리밋을 둘 수 있다(초과 시 `429 TOO_MANY_REQUESTS`).

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `roomId` | Long | 필수 | 채팅방 ID |

#### Request Body

```json
{
  "content": "안녕하세요, 내일 방문 가능할까요?"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `content` | string | 필수 | 공백 제외 1~1000자. 빈 문자열·공백만 불가 |

> 카드/시스템 메시지는 사용자가 보낼 수 없다(서버 전용). `type`을 본문으로 받지 않으며 항상 `TEXT`로 저장한다.

#### 성공 Response — 201 Created

```json
{
  "success": true,
  "data": {
    "messageId": 70051,
    "type": "TEXT",
    "senderId": 7,
    "mine": true,
    "content": "안녕하세요, 내일 방문 가능할까요?",
    "pinned": false,
    "sentAt": "2026-06-15T09:12:00Z"
  },
  "error": null
}
```

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | `content` 누락/빈값/길이 초과 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |
| 403 | `FORBIDDEN` | 요청자가 해당 방의 참여자가 아님 |
| 404 | `CHAT_ROOM_NOT_FOUND` | 방이 존재하지 않음 |
| 422 | `CHAT_ROOM_INACTIVE` | 비활성(차단/나간) 방에 전송. 차단·나가기 기능 도입 시에만 발생(현 범위에서는 트리거되지 않는 예약 코드) |
| 429 | `TOO_MANY_REQUESTS` | 메시지 도배(레이트리밋 초과) |

---

### 8. (후속·이연) POST `/api/v1/chat-rooms/{roomId}/read` — 읽음 처리

> **[후속·이연]** 인앱 채팅 결합으로 1차 MVP에서 이연. 대응 유저 스토리 US-4-5.

요청자의 "마지막 읽은 메시지" 위치를 갱신해 안읽음 수를 0으로 만든다. 상태 전이 액션이므로 동사형 서브경로를 쓴다(api-design-guide §1). 멱등적이다(같은 값으로 반복 호출해도 동일 결과).

- **인증**: 필수. **본인이 참여하지 않은 방이면 `403 FORBIDDEN`.**

#### Path 파라미터

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `roomId` | Long | 필수 | 채팅방 ID |

#### Request Body

```json
{
  "lastReadMessageId": 70051
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `lastReadMessageId` | Long | 선택 | 읽음으로 표시할 마지막 메시지 ID. 생략 시 방의 가장 최신 메시지까지 읽음 처리. 해당 방의 메시지여야 함. 현재 읽음 위치보다 과거 ID면 무시(전진만) |

#### 성공 Response — 200 OK

```json
{
  "success": true,
  "data": {
    "chatRoomId": 555,
    "lastReadMessageId": 70051,
    "unreadCount": 0
  },
  "error": null
}
```

#### 발생 가능한 에러

| status | code | 시점 |
| --- | --- | --- |
| 400 | `MALFORMED_REQUEST` | `lastReadMessageId`가 숫자가 아닌 타입(JSON 타입 불일치) |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 토큰 없음/만료 |
| 403 | `FORBIDDEN` | 요청자가 해당 방의 참여자가 아님 |
| 404 | `CHAT_ROOM_NOT_FOUND` | 방이 존재하지 않음 |
| 422 | `CHAT_MESSAGE_NOT_IN_ROOM` | `lastReadMessageId`가 해당 방의 메시지가 아님 |

---

## 도메인 에러 코드

> 공통 코드(`INVALID_INPUT`, `MALFORMED_REQUEST`, `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `TOO_MANY_REQUESTS` 등)는 [error-response-guide](../error-response-guide.md) §4를 따르며 여기서 재정의하지 않는다. 아래는 본 기능 고유 코드만 정의한다. prefix는 `BOOKING` / `CHAT`.

| code | status | 의미 | 스코프 |
| --- | --- | --- | --- |
| `BOOKING_INVALID_MOVE_IN_DATE` | 422 | 타겟 입주일이 과거이거나 매물의 입주 가능일 이전 | 1차 MVP |
| `BOOKING_NOT_FOUND` | 404 | 예약이 없거나 본인 예약이 아님(존재 여부를 노출하지 않도록 404로 통일) | 1차 MVP |
| `CHAT_ROOM_NOT_FOUND` | 404 | 채팅방이 존재하지 않음 | 후속·이연 |
| `CHAT_SELF_INQUIRY_NOT_ALLOWED` | 422 | 본인 소유 매물에 문의 시도 | 후속·이연 |
| `CHAT_ROOM_INACTIVE` | 422 | 비활성(차단/나간) 채팅방에 메시지 전송. 차단·나가기 기능 도입 시 활성화되는 예약 코드 | 후속·이연 |
| `CHAT_MESSAGE_NOT_IN_ROOM` | 422 | 읽음 처리 시 지정한 메시지가 해당 방에 속하지 않음 | 후속·이연 |

> 매물·방 상품 부재(`404`)는 listing 모듈의 `LISTING_NOT_FOUND` 코드를 참조해 응답한다. 해당 코드는 listing 스펙이 카탈로그에 등록하는 것을 원칙으로 하며, 본 기능에서는 재정의하지 않는다.
