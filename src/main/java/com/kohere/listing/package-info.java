/**
 * 매물 Bounded Context. 매물 리스트/지도/키워드 검색, 매물 상세, 찜 토글·찜 목록, 최근 본 매물과 임대인의 매물 등록을 담당한다.
 *
 * <p>등록({@code POST /api/v2/listings})은 multipart 요청으로 등록 정보와 사진을 함께 받아, 사진을 오브젝트 스토리지(배포는
 * S3+CloudFront, 로컬은 MinIO)에 먼저 올린 뒤 그 URL로 매물을 저장한다. 저장 포트는 {@code
 * domain.image.ListingImageStorage}, 어댑터는 {@code infrastructure.external.s3}이며 업로드나 저장이 실패하면 이미 올린
 * 객체를 보상 삭제한다(ADR-0041). {@code app.images.enabled=false}면 실제 저장소 대신 스텁이 등록돼 업로드 없이 URL만 만든다.
 *
 * <p>도메인 에러 코드 prefix: {@code LISTING}. 스펙: docs/api/specs/03-listings-favorites.md.
 *
 * <p>모듈 경계·계층 규칙은 docs/convention/code-style.md §3을 따른다. 공유 커널 {@code common}과 임대인 여부·표시 언어 조회를 위한
 * {@code user :: api}에만 의존한다. 다른 모듈이 매물 정보를 필요로 하면 listing 공개 API·이벤트로 가져간다. OPEN 모듈이라도 의존은 화이트리스트에
 * 명시해야 한다.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Listing",
    allowedDependencies = {"common", "user :: api"})
package com.kohere.listing;
