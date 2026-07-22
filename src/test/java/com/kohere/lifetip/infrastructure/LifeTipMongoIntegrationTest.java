package com.kohere.lifetip.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kohere.lifetip.application.LifeTipService;
import com.kohere.lifetip.application.dto.TipListResponse;
import com.kohere.lifetip.application.dto.TopicListResponse;
import com.kohere.lifetip.domain.LifeTipTopicNotFoundException;
import com.kohere.user.api.UserAccountService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 생활 팁 모듈 MongoDB 통합 테스트. 실제 Mongo 컨테이너로 주제·팁 영속·정렬·번역·en 폴백·사진 nullable·404를 end-to-end 검증한다. 표시
 * 언어(user getLanguage)는 mock으로 대체한다(test-strategy §4). Mongock(@ChangeUnit)은 슬라이스 테스트에서 비활성화한다.
 */
@DataMongoTest
@Testcontainers
@TestPropertySource(properties = "mongock.enabled=false")
@Import({LifeTipService.class, LifeTipCatalogImpl.class})
class LifeTipMongoIntegrationTest {

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  @Autowired LifeTipService lifeTipService;
  @Autowired LifeTipTopicMongoRepository topicRepository;
  @Autowired LifeTipMongoRepository tipRepository;

  @MockitoBean UserAccountService userAccountService;

  @BeforeEach
  void clean() {
    topicRepository.deleteAll();
    tipRepository.deleteAll();
  }

  @Test
  @DisplayName("주제 목록은 노출 순서로 정렬되고 사용자 표시 언어로 번역된다")
  void topicsTranslatedAndOrdered() {
    topicRepository.save(topic("TRANSPORT", 2, Map.of("en", "Transport", "ko", "교통")));
    topicRepository.save(topic("MOVING_IN", 1, Map.of("en", "Moving In", "ko", "입주")));
    given(userAccountService.getLanguage(1L)).willReturn("ko");

    TopicListResponse res = lifeTipService.getTopics(1L);

    assertThat(res.topics())
        .extracting(TopicListResponse.Topic::code)
        .containsExactly("MOVING_IN", "TRANSPORT");
    assertThat(res.topics().get(0).name()).isEqualTo("입주");
  }

  @Test
  @DisplayName("주제 목록은 짧은/긴 설명을 번역하고 이미지 URL은 언어 무관으로 그대로 싣는다")
  void topicsExposeDescriptionsAndImages() {
    topicRepository.save(
        topicWithMedia(
            "MOVING_IN",
            1,
            Map.of("en", "Moving In", "ko", "입주"),
            Map.of("en", "Basics.", "ko", "기본 정보."),
            Map.of("en", "Long guide.", "ko", "긴 안내."),
            "https://cdn.kohere.app/life-tips/topics/moving-in/card.png",
            "https://cdn.kohere.app/life-tips/topics/moving-in/background.png"));
    given(userAccountService.getLanguage(9L)).willReturn("ko");

    TopicListResponse.Topic t = lifeTipService.getTopics(9L).topics().get(0);

    assertThat(t.shortDescription()).isEqualTo("기본 정보.");
    assertThat(t.longDescription()).isEqualTo("긴 안내.");
    assertThat(t.imageUrl())
        .isEqualTo("https://cdn.kohere.app/life-tips/topics/moving-in/card.png");
    assertThat(t.backgroundImageUrl())
        .isEqualTo("https://cdn.kohere.app/life-tips/topics/moving-in/background.png");
  }

  @Test
  @DisplayName("미지원 언어는 영어(en)로 폴백하고 code는 언어 무관하다")
  void topicsFallbackToEnglish() {
    topicRepository.save(topic("MOVING_IN", 1, Map.of("en", "Moving In", "ko", "입주")));
    given(userAccountService.getLanguage(2L)).willReturn("ja");

    TopicListResponse res = lifeTipService.getTopics(2L);

    assertThat(res.topics().get(0).name()).isEqualTo("Moving In");
    assertThat(res.topics().get(0).code()).isEqualTo("MOVING_IN");
  }

  @Test
  @DisplayName("주제별 팁은 노출 순서로 번역돼 반환되고, 사진 없는 팁은 imageUrl=null")
  void tipsTranslatedWithNullableImage() {
    topicRepository.save(topic("MOVING_IN", 1, Map.of("en", "Moving In", "ko", "입주")));
    tipRepository.save(
        tip(
            "MOVING_IN",
            1,
            Map.of("en", "Registration", "ko", "전입신고"),
            Map.of("en", "Do it within 14 days.", "ko", "14일 이내에 하세요."),
            "http://img/1.png"));
    tipRepository.save(
        tip(
            "MOVING_IN",
            2,
            Map.of("en", "Utilities", "ko", "공과금"),
            Map.of("en", "Set up electricity.", "ko", "전기를 개통하세요."),
            null));
    given(userAccountService.getLanguage(3L)).willReturn("ko");

    TipListResponse res = lifeTipService.getTips(3L, "MOVING_IN");

    assertThat(res.tips()).extracting(TipListResponse.Tip::title).containsExactly("전입신고", "공과금");
    assertThat(res.tips().get(0).content()).isEqualTo("14일 이내에 하세요.");
    assertThat(res.tips().get(0).imageUrl()).isEqualTo("http://img/1.png");
    assertThat(res.tips().get(1).imageUrl()).isNull();
    assertThat(res.tips().get(0).id()).isNotBlank();
  }

  @Test
  @DisplayName("존재하지 않는 주제 코드로 팁을 조회하면 LIFE_TIP_TOPIC_NOT_FOUND")
  void tipsUnknownTopic() {
    assertThatThrownBy(() -> lifeTipService.getTips(4L, "UNKNOWN"))
        .isInstanceOf(LifeTipTopicNotFoundException.class);
  }

  @Test
  @DisplayName("게스트(userId=null)의 주제 목록은 en이고 user 모듈을 한 번도 호출하지 않는다")
  void guestTopicsUseEnglishWithoutCallingUser() {
    // 요점은 "기본값이 en"이 아니라 "호출 회피"다 — 게스트는 users 행이 없어 getLanguage가 곧 404다.
    topicRepository.save(topic("MOVING_IN", 1, Map.of("en", "Moving In", "ko", "입주")));

    TopicListResponse res = lifeTipService.getTopics(null);

    assertThat(res.topics().get(0).name()).isEqualTo("Moving In");
    verify(userAccountService, never()).getLanguage(anyLong());
    verify(userAccountService, never()).getUserType(anyLong());
  }

  @Test
  @DisplayName("게스트의 주제별 팁도 en이고 user 모듈 미호출")
  void guestTipsUseEnglishWithoutCallingUser() {
    topicRepository.save(topic("MOVING_IN", 1, Map.of("en", "Moving In", "ko", "입주")));
    tipRepository.save(
        tip(
            "MOVING_IN",
            1,
            Map.of("en", "Registration", "ko", "전입신고"),
            Map.of("en", "Do it within 14 days.", "ko", "14일 이내에 하세요."),
            null));

    TipListResponse res = lifeTipService.getTips(null, "MOVING_IN");

    assertThat(res.tips()).extracting(TipListResponse.Tip::title).containsExactly("Registration");
    verify(userAccountService, never()).getLanguage(anyLong());
    verify(userAccountService, never()).getUserType(anyLong());
  }

  @Test
  @DisplayName("임대인도 생활 팁을 본다 — 역할 게이트가 사라졌다(#181)")
  void landlordAllowed() {
    // 게이트를 되살리면 깨진다. 로그아웃하면 그대로 보이는 자원이라 로그인 임대인만 막는 것은 실효가 없다.
    topicRepository.save(topic("MOVING_IN", 1, Map.of("en", "Moving In", "ko", "입주")));
    given(userAccountService.getLanguage(77L)).willReturn("ko");

    TopicListResponse res = lifeTipService.getTopics(77L);

    assertThat(res.topics().get(0).name()).isEqualTo("입주");
    verify(userAccountService, never()).getUserType(anyLong());
  }

  @Test
  @DisplayName("주제는 존재하나 팁이 0건이면 빈 목록")
  void tipsEmpty() {
    topicRepository.save(topic("FINANCE", 1, Map.of("en", "Finance", "ko", "금융")));
    given(userAccountService.getLanguage(anyLong())).willReturn("en");
    assertThat(lifeTipService.getTips(5L, "FINANCE").tips()).isEmpty();
  }

  private static LifeTipTopicDocument topic(String code, int order, Map<String, String> name) {
    return topicWithMedia(
        code,
        order,
        name,
        Map.of("en", "short " + code, "ko", "짧은 " + code),
        Map.of("en", "long " + code, "ko", "긴 " + code),
        "https://cdn.kohere.app/life-tips/topics/" + code + "/card.png",
        "https://cdn.kohere.app/life-tips/topics/" + code + "/background.png");
  }

  private static LifeTipTopicDocument topicWithMedia(
      String code,
      int order,
      Map<String, String> name,
      Map<String, String> shortDescription,
      Map<String, String> longDescription,
      String imageUrl,
      String backgroundImageUrl) {
    return LifeTipTopicDocument.builder()
        .id(code)
        .name(name)
        .shortDescription(shortDescription)
        .longDescription(longDescription)
        .imageUrl(imageUrl)
        .backgroundImageUrl(backgroundImageUrl)
        .order(order)
        .build();
  }

  private static LifeTipDocument tip(
      String topicCode,
      int order,
      Map<String, String> title,
      Map<String, String> content,
      String imageUrl) {
    return LifeTipDocument.builder()
        .topicCode(topicCode)
        .order(order)
        .title(title)
        .content(content)
        .imageUrl(imageUrl)
        .build();
  }
}
