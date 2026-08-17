package com.kohere.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kohere.common.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

/** 등록 폼 1칸으로 받는 {@code min~max} 파싱 규칙을 검증한다. */
class RangeInputTest {

  /** 지점 운영층은 두 층 범위를 한 칸으로 받는다. */
  @Test
  void parse_운영층_범위를_두_값으로_나눈다() {
    RangeInput range = RangeInput.parse("building.usedFloorRange", "1~2");

    assertThat(range.min()).isEqualTo(1);
    assertThat(range.max()).isEqualTo(2);
  }

  /** 이용 연령대도 같은 형식을 쓴다. */
  @Test
  void parse_연령대_범위를_두_값으로_나눈다() {
    RangeInput range = RangeInput.parse("ageRange", "20~35");

    assertThat(range.min()).isEqualTo(20);
    assertThat(range.max()).isEqualTo(35);
  }

  /** 최소와 최대가 같은 한 층·한 살짜리 범위도 허용한다. */
  @Test
  void parse_최소와_최대가_같아도_허용한다() {
    RangeInput range = RangeInput.parse("ageRange", "20~20");

    assertThat(range.min()).isEqualTo(20);
    assertThat(range.max()).isEqualTo(20);
  }

  /** 사람이 넣기 쉬운 공백은 흡수한다 — 값 자체는 유효하기 때문이다. */
  @Test
  void parse_구분자_주변_공백을_허용한다() {
    RangeInput range = RangeInput.parse("ageRange", " 20 ~ 35 ");

    assertThat(range.min()).isEqualTo(20);
    assertThat(range.max()).isEqualTo(35);
  }

  /** 구분자가 없으면 두 값을 뽑을 수 없다. */
  @Test
  void parse_구분자가_없으면_거절한다() {
    assertThatThrownBy(() -> RangeInput.parse("ageRange", "20"))
        .isInstanceOf(InvalidInputException.class);
  }

  /** 구분자가 여러 개면 어느 쪽이 최대인지 정할 수 없다. */
  @Test
  void parse_구분자가_여러개면_거절한다() {
    assertThatThrownBy(() -> RangeInput.parse("ageRange", "20~30~35"))
        .isInstanceOf(InvalidInputException.class);
  }

  /** 숫자가 아닌 값은 층수·나이가 될 수 없다. */
  @Test
  void parse_숫자가_아니면_거절한다() {
    assertThatThrownBy(() -> RangeInput.parse("ageRange", "스무살~서른살"))
        .isInstanceOf(InvalidInputException.class);
  }

  /** 한쪽만 비어 있어도 두 값이 갖춰지지 않는다. */
  @Test
  void parse_한쪽_값이_비면_거절한다() {
    assertThatThrownBy(() -> RangeInput.parse("ageRange", "20~"))
        .isInstanceOf(InvalidInputException.class);
  }

  /** 최소가 최대보다 크면 범위가 성립하지 않는다. */
  @Test
  void parse_최소가_최대보다_크면_거절한다() {
    assertThatThrownBy(() -> RangeInput.parse("ageRange", "35~20"))
        .isInstanceOf(InvalidInputException.class);
  }

  /** 값 자체가 없으면 형식 오류가 아니라 필수값 누락이다. */
  @Test
  void parse_값이_비면_거절한다() {
    assertThatThrownBy(() -> RangeInput.parse("ageRange", "  "))
        .isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> RangeInput.parse("ageRange", null))
        .isInstanceOf(InvalidInputException.class);
  }
}
