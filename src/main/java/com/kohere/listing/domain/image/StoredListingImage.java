package com.kohere.listing.domain.image;

/**
 * 저장소에 올라간 사진 한 장이다.
 *
 * <p>키와 URL을 함께 들고 다닌다 — 매물 문서에 넣을 값은 URL이지만, 저장이 실패했을 때 지워야 할 대상은 키다. URL에서 키를 되뽑는 방식은 CDN 도메인 설정이
 * 바뀌는 순간 보상 삭제가 조용히 빗나가므로 쓰지 않는다.
 *
 * @param key 저장소 키
 * @param url 클라이언트가 읽을 수 있는 CDN URL
 */
public record StoredListingImage(String key, String url) {}
