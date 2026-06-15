package com.kohere.report.domain;

/** 신고 사유. docs/api/specs/07-reports.md (reason / GET /reasons 메타). */
public enum ReportReason {
  SPAM,
  ABUSE,
  SEXUAL_CONTENT,
  EXTERNAL_CONTACT,
  FALSE_INFO,
  ETC
}
