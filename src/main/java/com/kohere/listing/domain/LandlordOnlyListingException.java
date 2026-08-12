package com.kohere.listing.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 임대인이 아닌 회원이 매물 등록을 시도했을 때 던진다.
 *
 * <p>보안 필터는 {@code hasRole("USER")}로 정식 회원인지만 본다. 임대인 여부는 {@code user::api}의 {@code userType}으로
 * 서비스가 다시 확인한다.
 *
 * <p>{@code auth} 모듈에도 같은 뜻의 예외가 있지만 그쪽은 모듈 내부 타입이라 쓸 수 없다({@code listing}의 허용 의존은 {@code common}과
 * {@code user :: api}뿐이다). 응답 코드는 {@link ErrorCode#FORBIDDEN}으로 같다.
 */
public class LandlordOnlyListingException extends BusinessException {

  public LandlordOnlyListingException() {
    super(ErrorCode.FORBIDDEN);
  }
}
