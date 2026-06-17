/**
 * user 모듈의 공개 API(Named Interface "api"). 다른 모듈(예: auth)이 의존할 수 있는 공개 명령·쿼리·이벤트를 노출한다.
 *
 * <p>auth는 소셜 로그인/온보딩 흐름에서 {@link com.kohere.user.api.UserAccountService}를 동기 호출하고, 탈퇴 시 user가 발행하는
 * {@link com.kohere.user.api.UserWithdrawnEvent}를 구독한다(ADR-0002). 내부 도메인/영속 타입은 노출하지 않으며, 모듈 간
 * enum은 원시 문자열로 주고받는다(domain-model §1·§2).
 */
@org.springframework.modulith.NamedInterface("api")
package com.kohere.user.api;
