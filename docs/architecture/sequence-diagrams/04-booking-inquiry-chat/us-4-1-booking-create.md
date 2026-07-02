# US-4-1 — 매물 예약 생성(신청 저장)

> 모듈: 매물 예약(신청) · [유저 스토리](../../../requirements/user-stories.md) · [API 스펙](../../../api/specs/04-booking-inquiry-chat.md)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant C as 앱(클라이언트)
    participant SEC as 공통 보안 필터
    participant BOOK as booking 모듈
    participant LIST as listing::api
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
            MDB-->>LIST: 매물(roomOffer, 입주가능일) 또는 없음
            LIST-->>BOOK: 매물·roomOffer 정보 또는 없음

            alt 매물/방 상품 없음·비공개·삭제
                BOOK-->>C: 404 LISTING_NOT_FOUND
                C-->>U: 매물을 찾을 수 없음 안내
            else moveInDate 과거·입주가능일 이전
                Note over BOOK: moveInDate < 오늘 또는 입주가능일 이전 (형식은 유효)
                BOOK-->>C: 422 BOOKING_INVALID_MOVE_IN_DATE
                C-->>U: 입주일 확인 안내
            else 검증 통과 (정상)
                Note over BOOK: MVP 예약은 "신청" 성격 — 중복 제한 없음<br/>(활성 유니크 없이 append 저장)
                BOOK->>XDB: Booking 저장(status=REQUESTED)<br/>tenantId·listingId·roomOfferId·moveInDate·contractPeriod·createdAt
                XDB-->>BOOK: bookingId
                BOOK-->>C: 201 Created<br/>Location /api/v1/bookings/{bookingId}<br/>data: bookingId, status=REQUESTED, listingId,<br/>roomOfferId, moveInDate, contractPeriod, createdAt
                C-->>U: 예약 신청 완료 안내
            end
        end
    end
```

## 흐름 요약

- 보호 엔드포인트이므로 **공통 보안 필터(SEC)** 가 컨트롤러 앞단에서 `Authorization: Bearer <token>`의 JWT(서명·만료·클레임)를 검증한 뒤 인증된 요청(tenantId)을 **booking 모듈**로 전달한다. 토큰이 없거나 만료/위조면 필터가 `401 UNAUTHENTICATED`(만료 시 `TOKEN_EXPIRED`)로 막는다. 이후 booking 모듈이 요청자가 `ACTIVE`인지 다른 보호 엔드포인트와 **동일한 온보딩 상태 게이트**로 검사하고, 비`ACTIVE`(온보딩 미완료) 세입자는 `403 AUTH_ONBOARDING_REQUIRED`로 차단한다(코드 게이트와 1:1 일치).
- `ACTIVE` 세입자(`userType=TENANT`)가 `POST /api/v1/listings/{listingId}/bookings`로 `roomOfferId`·`moveInDate`·`contractPeriod`(개월수)를 보내면 **booking 모듈**이 **`listing::api`** 로 매물·방 상품 존재·공개를 동기 조회(`->>`)해 검증한다(소유자 조회 불요). `listingId`·`roomOfferId`는 MongoDB ObjectId 문자열이며, 매물·`roomOffer` 조회는 listing 모듈이 MongoDB에서, 예약 저장은 booking 모듈이 자기 저장소에서 각자 자기 데이터를 읽고 쓴다(cross-store 조인 금지, [ADR-0005](../../../adr/0005-polyglot-persistence.md)).
- 검증 통과 시 booking 모듈이 `Booking`을 `REQUESTED` 상태로 **저장소**에 생성하고 `201 Created` + `Location: /api/v1/bookings/{bookingId}`와 `bookingId`·`status=REQUESTED`·`listingId`·`roomOfferId`·`moveInDate`·`contractPeriod`·`createdAt`를 반환한다. **MVP의 예약은 "신청" 성격이라 중복 제한이 없다** — 활성 유니크 제약 없이 append 저장하며, 같은 방 상품에도 여러 신청이 가능하다(`BOOKING_ALREADY_EXISTS` 없음).
- 인증 실패는 `401`, 비세입자(임대인)는 `403 FORBIDDEN`, 매물/방 상품 부재·비공개·삭제는 `404 LISTING_NOT_FOUND`, 과거·입주가능일 이전 입주일은 `422 BOOKING_INVALID_MOVE_IN_DATE`로 차단된다. 이들 권한·비즈니스 규칙 판단은 필터가 아니라 booking 모듈의 몫이며, 차단된 경로에서는 저장소에 도메인 상태를 쓰지 않는다.
- **예약 카드(`BOOKING_CARD`) 자동 전송·채팅방 보장·`BookingCreatedEvent` 발행·푸시 알림은 후속(문의·인앱 채팅)으로 이연**한다 — 본 스토리(1차 MVP)에서는 예약 저장만 수행한다.

> 저장소: 트랜잭션·유니크 제약·숫자 PK가 필요해 MySQL이 유력하나, [ADR-0005](../../../adr/0005-polyglot-persistence.md) 폴리글랏 표의 `booking` 매핑은 아직 "추후 결정"이다 — **(확인 필요)**.
