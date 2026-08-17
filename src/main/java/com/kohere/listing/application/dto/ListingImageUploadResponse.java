package com.kohere.listing.application.dto;

/**
 * 사진 업로드 응답({@code POST /api/v2/listings/images}).
 *
 * <p>두 값의 쓰임이 다르다 — {@code url}은 폼에서 미리보기로 띄우는 값이고, {@code key}는 등록 요청에 되돌려 보내는 값이다. 등록이 끝나면 사진이 확정
 * 위치로 옮겨 가므로 이 {@code url}은 곧 무효가 된다(ADR-0041 §1).
 *
 * @param key 저장 키. 매물 등록 요청의 {@code imageKeys}·{@code roomOffers[].roomImageKeys}에 담는다
 * @param url 미리보기 주소
 */
public record ListingImageUploadResponse(String key, String url) {}
