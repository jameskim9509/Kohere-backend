package com.kohere.listing.domain.image;

import java.io.InputStream;

/**
 * 저장소에 올릴 사진 한 장이다.
 *
 * <p>바이트를 담지 않고 스트림으로 넘긴다. 한 요청이 최대 수십 장을 실어 보낼 수 있어 전부 배열로 들고 있으면 요청 하나가 힙을 크게 차지한다.
 *
 * @param key 저장 키. {@link ListingImageKeys}가 만든다
 * @param contentType 저장소에 그대로 기록할 미디어 타입
 * @param contentLength 바이트 크기. 저장소가 스트림 길이를 미리 알아야 한다
 * @param content 파일 내용. 어댑터가 한 번만 읽고 닫는다
 */
public record ListingImageUpload(
    String key, ListingImageContentType contentType, long contentLength, InputStream content) {}
