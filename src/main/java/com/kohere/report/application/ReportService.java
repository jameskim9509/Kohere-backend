package com.kohere.report.application;

import com.kohere.report.application.dto.ReasonListResponse;
import com.kohere.report.application.dto.ReportResponse;
import com.kohere.report.domain.ReportRepository;
import com.kohere.report.presentation.dto.CreateReportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 콘텐츠 신고 유스케이스 조율. 도메인(포트)을 호출하고 흐름만 조율한다. 도메인 규칙은 엔티티/도메인 서비스에 둔다 (docs/convention/code-style.md
 * §3-3).
 *
 * <p>의존성은 생성자 주입({@code @RequiredArgsConstructor})으로 받는다(§3-4). 신고자(reporterId)는 SecurityContext의
 * 인증 주체에서 가져온다(TODO: 보안 설정 후 연동).
 *
 * <p>TODO: 신고 대상 존재검증(404)·자기 신고 차단(422)은 다른 모듈 정보가 필요하므로 공개 API·이벤트로 연결한다(모듈 경계 유지).
 *
 * <p>TODO: 영속 계층(JPA) 도입 시 유스케이스에 트랜잭션 경계({@code @Transactional})를 추가한다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

  private final ReportRepository reportRepository;

  public ReasonListResponse getReasons() {
    throw new UnsupportedOperationException("TODO: 신고 사유 enum 메타 목록 조회");
  }

  public ReportResponse createReport(CreateReportRequest request) {
    throw new UnsupportedOperationException("TODO: 콘텐츠 신고 접수·저장(중복 거부, 상태 RECEIVED 고정)");
  }
}
