package com.kohere.lifetip.application;

import com.kohere.lifetip.application.dto.TipListResponse;
import com.kohere.lifetip.application.dto.TopicListResponse;
import com.kohere.lifetip.domain.LifeTip;
import com.kohere.lifetip.domain.LifeTipCatalog;
import com.kohere.lifetip.domain.LifeTipTopic;
import com.kohere.lifetip.domain.LifeTipTopicNotFoundException;
import com.kohere.user.api.UserAccountService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 생활 팁 조회 유스케이스 조율. 운영이 시드로 적재한 읽기 전용 큐레이션 카탈로그를 사용자 표시 언어로 번역해 내려준다(US-8, ADR-0029). 회원의 표시 언어는
 * {@link UserAccountService#getLanguage(long)} 동기 공개 쿼리로 취득하고({@code users.lang}, 미설정이면 en —
 * ADR-0002 D5·#141), 미지원 언어는 영어({@code en})로 폴백한다(에러 아님).
 *
 * <p><b>역할 게이트가 없다</b> — 비회원(게스트)까지 열린 {@code permitAll} 경로이므로 세입자·임대인·게스트가 모두 조회할 수 있다(#181). 로그인한
 * 임대인만 403으로 막는 것은 로그아웃하면 그대로 볼 수 있는 이상 실효가 없다.
 *
 * <p>게스트는 {@code userId == null}(신원 부재)로 들어오며 표시 언어는 {@code en} 고정이다 — {@code users} 행이 없어 {@code
 * getLanguage}가 {@code 404 USER_NOT_FOUND}가 되므로 <b>호출 자체를 건너뛴다</b>. 온보딩 미완료 토큰은 {@code users} 행이
 * 있으므로 게스트가 아니라 {@code users.lang}을 따른다.
 *
 * <p>읽기 전용이라 상태 변경·발행 이벤트가 없다. 스펙: docs/api/specs/08-life-tips.md.
 */
@Service
@RequiredArgsConstructor
public class LifeTipService {

  /** 표시 문자열 언어-키 맵에서 사용자 언어 값이 없을 때의 폴백 언어이자, 게스트의 고정 표시 언어(ADR-0029·#181). */
  private static final String DEFAULT_LANGUAGE = "en";

  private final LifeTipCatalog lifeTipCatalog;
  private final UserAccountService userAccountService;

  /**
   * 주제 전체 목록(노출 순서, 사용자 표시 언어로 표시명 번역).
   *
   * @param userId 회원이면 userId, 비회원(게스트)이면 {@code null}(표시 언어 {@code en} 고정)
   */
  public TopicListResponse getTopics(Long userId) {
    String language = resolveLanguage(userId);
    List<TopicListResponse.Topic> topics =
        lifeTipCatalog.findAllTopics().stream().map(t -> toTopic(t, language)).toList();
    return new TopicListResponse(topics);
  }

  /**
   * 특정 주제의 팁 전체(노출 순서, 제목·내용 번역). 미존재 주제는 {@code LIFE_TIP_TOPIC_NOT_FOUND}(404).
   *
   * @param userId 회원이면 userId, 비회원(게스트)이면 {@code null}(표시 언어 {@code en} 고정)
   */
  public TipListResponse getTips(Long userId, String topicCode) {
    if (!lifeTipCatalog.topicExists(topicCode)) {
      throw new LifeTipTopicNotFoundException();
    }
    String language = resolveLanguage(userId);
    List<TipListResponse.Tip> tips =
        lifeTipCatalog.findTipsByTopicCode(topicCode).stream()
            .map(t -> toTip(t, language))
            .toList();
    return new TipListResponse(tips);
  }

  /**
   * 표시 언어 결정. 게스트({@code userId == null})는 {@code users} 행이 없어 {@code getLanguage}가 404가 되므로 호출하지
   * 않고 {@code en}을 쓴다(#181).
   */
  private String resolveLanguage(Long userId) {
    return userId == null ? DEFAULT_LANGUAGE : userAccountService.getLanguage(userId);
  }

  private static TopicListResponse.Topic toTopic(LifeTipTopic topic, String language) {
    return new TopicListResponse.Topic(
        topic.code(),
        pickLabel(topic.name(), language),
        pickLabel(topic.shortDescription(), language),
        pickLabel(topic.longDescription(), language),
        topic.imageUrl(),
        topic.backgroundImageUrl());
  }

  private static TipListResponse.Tip toTip(LifeTip tip, String language) {
    return new TipListResponse.Tip(
        tip.id(),
        pickLabel(tip.title(), language),
        pickLabel(tip.content(), language),
        tip.imageUrl());
  }

  private static String pickLabel(Map<String, String> labels, String language) {
    if (labels == null) {
      return null;
    }
    String value = labels.get(language);
    if (value != null) {
      return value;
    }
    return labels.getOrDefault(DEFAULT_LANGUAGE, labels.values().stream().findFirst().orElse(null));
  }
}
