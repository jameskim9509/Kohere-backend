package com.kohere.auth.domain;

import com.kohere.common.exception.BusinessException;
import com.kohere.common.exception.ErrorCode;

/**
 * 임대인 웹 회원가입 제출 시 {@code email}의 가입용 인증 마커가 없음(미인증·만료·이미 소비). 전역 핸들러가 422 {@code
 * AUTH_EMAIL_NOT_VERIFIED}로 변환한다(가입용 이메일 인증 선행 필요, 스펙 §1-11·§1-12).
 *
 * <p><b>이 코드는 한 번 지워졌다가 되살아났다.</b> 원래는 온보딩 제출이 세입자 이메일 인증을 게이트하던 시절의 코드였는데, #192가 그 게이트를 폐지하면서 enum
 * 상수와 예외 타입이 함께 사라졌다(메시지 번들 2벌에는 키가 남아 있었다). 여기서 <b>새 코드를 만들지 않고 같은 코드를 재사용</b>하는 이유는 {@link
 * PhoneNotVerifiedException}이 임대인 온보딩과 웹 가입 양쪽에서 공유되는 선례가 있어서다 — 뜻이 "그 연락 수단의 소유가 아직 증명되지 않았다"로 같으면
 * 경로마다 코드를 늘리지 않는다.
 *
 * <p><b>이 게이트가 지키는 것은 남의 계정이 아니라 본인의 복구 경로다.</b> {@link PhoneNotVerifiedException}은 "인증 없이 번호만으로 남의
 * 계정에 자격증명이 붙는 것"을 막지만, 이쪽은 그런 탈취 경로가 없다(이메일은 연동 매칭 키가 아니다). 대신 닿지 않는 주소로 가입한 계정은 <b>비밀번호를 반복 실패해
 * 잠기는 순간 재설정 메일을 받을 수 없어 자력 해제가 불가능해진다</b> — 잠금은 시간으로 풀리지 않으므로 그때는 운영자가 DB를 직접 고치는 길밖에 남지 않는다.
 */
public class EmailNotVerifiedException extends BusinessException {

  public EmailNotVerifiedException() {
    super(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
  }
}
