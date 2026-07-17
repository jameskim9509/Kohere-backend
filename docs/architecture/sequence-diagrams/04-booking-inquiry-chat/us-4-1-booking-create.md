# US-4-1 — 매물 예약 생성(신청 저장)

> 모듈: 매물 예약(신청) · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/04-booking-inquiry-chat.md)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant BOOK as booking 모듈
    participant LIST as listing::api
    participant USER as user::api
    participant MDB as MongoDB
    participant XDB as 저장소(booking, 추후 결정)

    U->>C: 방 상품·타겟 입주일·계약기간 선택 후 신청
    C->>SEC: POST /api/v1/listings/{listingId}/bookings<br/>Authorization: Bearer <token><br/>roomOfferId, moveInDate=2026-07-01, contractPeriod=6
    Note over SEC: JWT 검증 (서명·만료·클레임)

    alt 토큰 없음/만료/위조
        SEC-->>C: 401 UNAUTHENTICATED (만료 시 TOKEN_EXPIRED)
        C-->>U: 로그인 필요/세션 만료 안내
    else 토큰 유효
        SEC->>BOOK: 인증된 요청 전달 (tenantId)
        Note over BOOK: 온보딩·역할 게이트 검사<br/>(다른 보호 엔드포인트와 동일: 비 ACTIVE·비 TENANT 차단)

        alt 온보딩 미완료 (비 ACTIVE)
            BOOK-->>C: 403 AUTH_ONBOARDING_REQUIRED
            C-->>U: 온보딩 완료 필요 안내
        else 비세입자 (임대인 등)
            Note over BOOK: 예약은 세입자 전용<br/>(임대인은 예약 불가 → 자기 매물 예약 상황 자체가 없음)
            BOOK-->>C: 403 FORBIDDEN
            C-->>U: 세입자만 예약 가능 안내
        else ACTIVE 세입자
            BOOK->>LIST: 매물·방 상품 존재·공개 검증(listingId, roomOfferId)
            LIST->>MDB: ObjectId listingId로 매물 조회<br/>+ roomOfferId 방 상품·입주가능일 확인
            MDB-->>LIST: 매물(roomOffer, 입주가능일, landlordId) 또는 없음
            LIST-->>BOOK: 매물·roomOffer·landlordId 정보 또는 없음

            alt 매물/방 상품 없음·비공개·삭제
                BOOK-->>C: 404 LISTING_NOT_FOUND
                C-->>U: 매물을 찾을 수 없음 안내
            else moveInDate 과거·입주가능일 이전
                Note over BOOK: moveInDate < 오늘 또는 입주가능일 이전 (형식은 유효)
                BOOK-->>C: 422 BOOKING_INVALID_MOVE_IN_DATE
                C-->>U: 입주일 확인 안내
            else 검증 통과 (정상)
                BOOK->>USER: isBlockedBetween(tenantId, listing.landlordId)
                USER-->>BOOK: 차단 관계 여부(양방향)

                alt 차단 관계(어느 방향이든)
                    Note over BOOK: 블랙홀 예약 방지 — 차단한 상대의 매물에 신청하면<br/>201이 나가도 목록엔 영영 보이지 않는다(양쪽 목록에서 서로 가려짐)
                    BOOK-->>C: 403 FORBIDDEN
                    C-->>U: 차단 관계로 신청 불가 안내
                else 차단 없음
                    Note over BOOK: 중복 방지 — 동일 세입자·동일 방 상품 활성 1건만 허용<br/>UNIQUE(tenant_id, room_offer_id), 재신청은 409로 막힌다
                    alt 이미 신청한 방 상품(중복)
                        BOOK-->>C: 409 BOOKING_ALREADY_EXISTS
                        C-->>U: 이미 신청한 매물 안내
                    else 신규 신청
                        BOOK->>XDB: Booking 저장(status=REQUESTED)<br/>tenantId·listingId·roomOfferId·moveInDate·contractPeriod·createdAt
                        XDB-->>BOOK: bookingId
                        BOOK-->>C: 201 Created<br/>Location /api/v1/bookings/{bookingId}<br/>data: bookingId, status=REQUESTED, listingId,<br/>roomOfferId, moveInDate, contractPeriod, createdAt
                        C-->>U: 예약 신청 완료 안내
                    end
                end
            end
        end
    end
```

## 흐름 요약

- 보호 엔드포인트이므로 **공통 보안 필터(SEC)** 가 컨트롤러 앞단에서 `Authorization: Bearer <token>`의 JWT(서명·만료·클레임)를 검증한 뒤 인증된 요청(tenantId)을 **booking 모듈**로 전달한다. 토큰이 없거나 만료/위조면 필터가 `401 UNAUTHENTICATED`(만료 시 `TOKEN_EXPIRED`)로 막는다. 이후 booking 모듈이 요청자가 `ACTIVE`인지 다른 보호 엔드포인트와 **동일한 온보딩 상태 게이트**로 검사하고, 비`ACTIVE`(온보딩 미완료) 세입자는 `403 AUTH_ONBOARDING_REQUIRED`로 차단한다(코드 게이트와 1:1 일치).
- `ACTIVE` 세입자(`userType=TENANT`)가 `POST /api/v1/listings/{listingId}/bookings`로 `roomOfferId`·`moveInDate`·`contractPeriod`(개월수)를 보내면 **booking 모듈**이 **`listing::api`** 로 매물·방 상품 존재·공개를 동기 조회(`->>`)해 검증한다(차단 가드 판정에 쓸 소유자 `landlordId`도 함께 받는다). `listingId`·`roomOfferId`는 MongoDB ObjectId 문자열이며, 매물·`roomOffer` 조회는 listing 모듈이 MongoDB에서, 예약 저장은 booking 모듈이 자기 저장소에서 각자 자기 데이터를 읽고 쓴다(cross-store 조인 금지, [ADR-0005](../../../adr/0005-polyglot-persistence.md)).
- 매물·방 상품 검증을 통과하면 **예약을 저장하기 전에 차단 가드(양방향)** 가 선다 — booking 모듈이 **`user::api`** 의 `isBlockedBetween(tenantId, listing.landlordId)`로 요청자와 매물 소유자 사이의 차단 관계를 **어느 방향이든** 판정하고, 참이면 `403 FORBIDDEN`으로 막는다. 차단은 예약 단위가 아니라 **사용자 단위 전역**이다. 주된 근거는 **구조적**이다 — 임대인은 매물(`Listing`)을 여럿, 매물은 방 상품(`roomOffer`)을 여럿 가지므로 방·예약 단위 차단은 상대의 **다른 방**으로 얼마든지 우회된다. (보조로) 같은 방 재신청은 이제 `UNIQUE(tenant_id, room_offer_id)`로 막히지만, 임대인은 방·매물을 여럿 가지므로 **다른 방으로는 여전히 우회된다** — 그래서 여전히 사용자 단위여야 한다. 가드가 없으면 **블랙홀 예약**이 생긴다: 차단한 상대의 매물에 신청하면 `201`이 나가도 그 예약은 양쪽 목록에서 서로 가려져 **영영 보이지 않는다**.
- 가드까지 통과하면 booking 모듈이 **중복 방지**를 판정한다 — 동일 세입자·동일 방 상품에 예약(신청)은 **1건만** 허용되며, 이미 신청한 방 상품에 재신청하면 `409 BOOKING_ALREADY_EXISTS`("이미 신청한 매물입니다")로 막힌다. 이는 `bookings`의 `UNIQUE(tenant_id, room_offer_id)` 제약(`uq_bookings_tenant_room_offer`, `V18__add_bookings_unique_tenant_room_offer.sql` 예정 · V17은 신고 사유 카탈로그)으로 강제한다. `BookingStatus` 전이(수락·거절·취소)가 아직 미구현이라 **모든 예약이 `REQUESTED`(=활성)** 이므로 "활성 1건"과 "전체 1건"이 같아, 조건 없는 `UNIQUE`로 규칙이 정확히 표현된다. `BOOKING_ALREADY_EXISTS`(409)는 `ErrorCode`·메시지 번들에 이미 선언돼 있으나 아무도 던지지 않던 코드로, 이 결정으로 **실제 사용(live)** 으로 전환된다(신규 코드가 아니다). 중복 아닌 신규 신청이면 `Booking`을 `REQUESTED` 상태로 **저장소**에 생성하고 `201 Created` + `Location: /api/v1/bookings/{bookingId}`와 `bookingId`·`status=REQUESTED`·`listingId`·`roomOfferId`·`moveInDate`·`contractPeriod`·`createdAt`를 반환한다.
    - ⚠️ 향후: 상태 전이가 도입되면 `REJECTED`·`CANCELED` 건이 그 방 재신청을 영구 차단하므로 **활성 상태만 대상으로 하는 부분 유니크**로 교체해야 한다. MySQL은 부분 유니크 인덱스를 지원하지 않아(nullable `active_room_offer_id` 컬럼 + `UNIQUE` 트릭, 또는 앱 레벨 검사 등) 표현 방식은 그때 정한다. 제약 강화는 비호환 변경이라 기존 중복 행 정리가 선행돼야 하나(`bookings`는 신규라 사실상 비어 있다).
- 인증 실패는 `401`, 비세입자(임대인)는 `403 FORBIDDEN`, 매물/방 상품 부재·비공개·삭제는 `404 LISTING_NOT_FOUND`, 과거·입주가능일 이전 입주일은 `422 BOOKING_INVALID_MOVE_IN_DATE`, **요청자와 매물 소유자 사이의 차단 관계(양방향)는 `403 FORBIDDEN`**, **이미 신청한 방 상품 재신청(중복)은 `409 BOOKING_ALREADY_EXISTS`** 로 차단된다. 이들 권한·비즈니스 규칙 판단은 필터가 아니라 booking 모듈의 몫이며, 차단된 경로에서는 저장소에 도메인 상태를 쓰지 않는다.
- **예약 카드(`BOOKING_CARD`) 자동 전송·채팅방 보장·`BookingCreatedEvent` 발행·푸시 알림은 후속(문의·인앱 채팅)으로 이연**한다 — 본 스토리(1차 MVP)에서는 예약 저장만 수행한다.

> 신설 의존: 차단 가드가 `user::api`의 `isBlockedBetween(a, b)`(공개 쿼리, 양방향 판정)를 새로 호출한다. **`booking → user :: api`는 이미 화이트리스트**라 새 모듈 의존 엣지가 생기지 않는다 — 기존 허용 의존 위에 호출 하나가 얹힐 뿐이다(`user_blocks`는 `user` 소유이며 booking은 저장소를 직접 읽지 않는다, [ADR-0005](../../../adr/0005-polyglot-persistence.md)).

> 저장소: `booking`은 **MySQL로 이미 배포된 사실**이다(`V9__bookings.sql`·`V11__add_bookings_landlord_id.sql`) — 트랜잭션·유니크 제약·숫자 PK 요구와도 맞는다. 다만 [ADR-0005](../../../adr/0005-polyglot-persistence.md) 폴리글랏 배치 표에 이 배치를 반영하는 일은 **아직 열려 있다**(본 문서가 그 ADR 결정을 대신하지 않는다).
