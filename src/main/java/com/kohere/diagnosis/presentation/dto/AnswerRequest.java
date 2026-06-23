package com.kohere.diagnosis.presentation.dto;

import java.util.Set;

/**
 * 단계별 답 제출 요청 바디. 현재 step의 답을 {@code field}와 함께 보내면 서버가 진행 중(in-progress) 진단에 저장한다. 단일 선택은 {@code
 * code}, 다중 선택(예: conditions)은 {@code codes} 집합으로 보낸다(둘 중 해당하는 쪽만 채우며 나머지는 null). 누적답을 묶어 재전송하지 않는다
 * — 서버가 in-progress에 저장한다.
 *
 * <p>미정의 enum·목적-대학/지역 불일치 등 잘못된 답은 공통 {@code INVALID_INPUT}(400) + {@code errors[]}로 변환한다(검증 로직은
 * 응용 계층 TODO).
 *
 * <p>docs/api/specs/02-diagnosis-recommendation.md.
 *
 * @param field 답을 저장할 제출 필드명(예:
 *     region·purpose·university·district·conditions·monthlyBudgetMax·arcStatus)
 * @param code 단일 선택 답의 코드(enum 이름 또는 값; 다중일 땐 null)
 * @param codes 다중 선택 답의 코드 집합(단일일 땐 null)
 */
public record AnswerRequest(String field, String code, Set<String> codes) {}
