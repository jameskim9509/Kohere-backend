package com.kohere.listing.domain.nearby;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 인근 장소 검색 제공자의 장애 또는 응답 계약 위반을 나타낸다(ADR-0044).
 *
 * <p>결과가 없는 정상 응답({@code documents=[]})과 외부 장애를 구분하기 위한 예외다. 전역 예외 처리기가 {@code 502 UPSTREAM_ERROR}로
 * 변환하므로 프론트는 빈 결과 화면 대신 재시도 가능한 장애 상태를 보여줄 수 있다.
 *
 * <p>장소·주소 검색의 같은 모양 예외와 타입을 나누는 이유는 제공자·자격증명이 서로 달라 로그에서 어느 연동이 죽었는지 구분되어야 하기 때문이다.
 */
public class NearbyPlaceSearchUpstreamException extends BusinessException {

  /**
   * 원인이 된 HTTP·파싱·설정 예외를 보존해 서버 로그에서 장애 원인을 추적할 수 있게 한다.
   *
   * @param cause 외부 연동 실패의 실제 원인. 클라이언트 응답에는 노출되지 않는다
   */
  public NearbyPlaceSearchUpstreamException(Throwable cause) {
    super(ErrorCode.UPSTREAM_ERROR);
    initCause(cause);
  }
}
