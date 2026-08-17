package com.kohere.common.request;

/**
 * 요청으로 들어온 휴대폰 번호를 저장·비교용 표준형(숫자만)으로 접는다.
 *
 * <p><b>왜 정규화가 필요한가</b> — 같은 사람이 {@code 010-1234-5678}과 {@code 01012345678}을 번갈아 보내면 표기만 다른 같은 번호가
 * 서로 다른 값으로 남는다. 그러면 {@code users.phone_number}의 UNIQUE(V23)가 계정이 갈라지는 것을 막지 못하고, SMS 인증 마커 대조도 문자열
 * 동등성이라 표기가 어긋나는 순간 조용히 실패한다. 그래서 <b>입력 경로에서 한 번</b> 접어 넣고 이후 저장·비교·키 생성은 전부 이 형태로만 한다.
 *
 * <p><b>기존 행은 백필하지 않는다</b>(#229 D10) — 하이픈으로 저장돼 있던 임대인 번호는 정규화된 값과 매칭되지 않을 수 있다. 알려진 제약이며
 * docs/requirements/user-stories.md에 기록돼 있다(해당 행은 연락처를 다시 인증해 바꾸는 시점에 표준형으로 접힌다).
 *
 * <p>형식 위반의 거부는 여기서 하지 않는다 — 요청 DTO의 {@code @NotBlank} + {@code @Pattern}({@link #PATTERN})이 맡고,
 * 그쪽도 {@code INVALID_INPUT}이라 응답 모양이 같다({@link RequestDates}와 같은 분업).
 */
public final class PhoneNumbers {

  /**
   * 요청 DTO {@code @Pattern}용 국내 휴대폰 번호 형식 — 하이픈은 선택이다({@code 01012345678}·{@code 010-1234-5678} 모두
   * 통과). 국가번호 표기({@code +82-10-…})는 받지 않는다 — 숫자만 남기면 {@code 8210…}이 돼 {@code 010…}과 다른 값이 되므로, 정규화가
   * 한 형태로 수렴하도록 입구에서 막는다.
   */
  public static final String PATTERN = "^01[016789]-?\\d{3,4}-?\\d{4}$";

  private PhoneNumbers() {}

  /**
   * 숫자가 아닌 문자를 모두 지운 표준형으로 접는다(예 {@code 010-1234-5678} → {@code 01012345678}). 멱등이라 이미 접힌 값을 다시 넣어도
   * 결과가 같다 — 경로 여러 곳에서 겹쳐 불러도 안전하다.
   *
   * @param phoneNumber 미전송({@code null})이면 {@code null}을 돌려준다(선택 필드의 미변경)
   */
  public static String normalize(String phoneNumber) {
    return phoneNumber == null ? null : phoneNumber.replaceAll("\\D", "");
  }
}
