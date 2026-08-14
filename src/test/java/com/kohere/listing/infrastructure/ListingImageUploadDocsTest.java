package com.kohere.listing.infrastructure;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.resourceDetails;
import static com.kohere.docs.ApiDocsErrors.errorSnippet;
import static com.kohere.docs.DocsTokens.bearer;
import static com.kohere.docs.DocsTokens.expiredAccessToken;
import static com.kohere.docs.ListingDocsFields.LISTING_IMAGE_UPLOAD_400;
import static com.kohere.docs.ListingDocsFields.LISTING_IMAGE_UPLOAD_401;
import static com.kohere.docs.ListingDocsFields.LISTING_IMAGE_UPLOAD_403;
import static com.kohere.docs.ListingDocsFields.LISTING_IMAGE_UPLOAD_413;
import static com.kohere.docs.ListingDocsFields.LISTING_IMAGE_UPLOAD_415;
import static com.kohere.docs.ListingDocsFields.LISTING_IMAGE_UPLOAD_DESCRIPTION;
import static com.kohere.docs.ListingDocsFields.LISTING_IMAGE_UPLOAD_SUMMARY;
import static com.kohere.docs.ListingDocsFields.imageUploadResponseFields;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kohere.TestcontainersConfiguration;
import com.kohere.common.security.JwtProperties;
import com.kohere.common.security.JwtTokenService;
import com.kohere.docs.ApiDocsTags;
import com.kohere.listing.domain.image.PendingListingImage;
import com.kohere.user.api.UserAccountService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.operation.preprocess.ContentModifyingOperationPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 매물 사진 업로드({@code POST /api/v2/listings/images})의 REST Docs 스니펫 생성 테스트다.
 *
 * <p>등록과 파일을 나눠 받는 이유가 파일별 진행률·재시도라서 검증도 한 장 단위다(ADR-0041 §1). 형식·크기 위반이 <b>저장소를 부르기 전에</b> 걸리는지가
 * 핵심이다 — 검증이 업로드보다 앞선다는 성질에 "거절된 요청은 흔적을 남기지 않는다"가 기대어 있다.
 *
 * <p>저장소는 test 프로파일에서 {@code app.images.enabled=false}라 스텁이 붙는다. 실제 S3 프로토콜은 {@code
 * S3ListingImageStorageIntegrationTest}가 MinIO로 검증한다.
 */
@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestcontainersConfiguration.class)
class ListingImageUploadDocsTest {

  private static final long LANDLORD_ID = 42L;
  private static final long TENANT_ID = 1L;

  private static final String IMAGE_CONTENT_TYPE = "image/jpeg";
  private static final byte[] IMAGE_BYTES = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);

  /** 다른 키로 서명한 위조 토큰. 401 예시도 JWT여야 restdocs-api-spec이 bearerAuth 스킴을 도출한다. */
  private static final String FORGED_TOKEN =
      Jwts.builder()
          .issuer("kohere")
          .subject("1")
          .claim("onboardingCompleted", true)
          .signWith(
              Keys.hmacShaKeyFor(
                  "forged-doc-only-wrong-secret-please-override-32bytes-min!!"
                      .getBytes(StandardCharsets.UTF_8)))
          .compact();

  @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  @Autowired private WebApplicationContext context;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private JwtProperties jwtProperties;

  @MockitoBean private UserAccountService userAccountService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    BDDMockito.given(userAccountService.getUserType(LANDLORD_ID)).willReturn("LANDLORD");
    BDDMockito.given(userAccountService.getUserType(TENANT_ID)).willReturn("TENANT");
  }

  /** 성공 — 키는 내 임시 영역에 놓이고, 미리보기 URL은 그 키를 가리킨다. */
  @Test
  void 문서스니펫생성_사진업로드_임시키와_미리보기URL을_돌려준다() throws Exception {
    mockMvc
        .perform(
            upload(landlordToken(), imagePart("branch-1.jpg", IMAGE_CONTENT_TYPE, IMAGE_BYTES)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.key").value(org.hamcrest.Matchers.startsWith("uploads/42/")))
        .andExpect(jsonPath("$.data.key").value(org.hamcrest.Matchers.endsWith(".jpg")))
        .andExpect(
            jsonPath("$.data.url").value(org.hamcrest.Matchers.containsString("/uploads/42/")))
        .andDo(
            document(
                "listing-image-upload",
                resourceDetails()
                    .tag(ApiDocsTags.LISTINGS)
                    .summary(LISTING_IMAGE_UPLOAD_SUMMARY)
                    .description(LISTING_IMAGE_UPLOAD_DESCRIPTION),
                hideMultipartBody(),
                null,
                Function.identity(),
                responseFields(imageUploadResponseFields())));
  }

  /** 형식은 파일 내용이 아니라 part의 Content-Type으로 본다 — 확장자를 바꿔 올려도 형식이 맞으면 통과한다. */
  @Test
  void 사진업로드_허용형식이면_확장자가_달라도_받는다() throws Exception {
    mockMvc
        .perform(upload(landlordToken(), imagePart("photo.heic", "image/heic", IMAGE_BYTES)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.key").value(org.hamcrest.Matchers.endsWith(".heic")));
  }

  @Test
  void 문서스니펫생성_업로드실패조건_status와errorcode가일치() throws Exception {
    performError(
        upload(bearer(FORGED_TOKEN), imagePart("a.jpg", IMAGE_CONTENT_TYPE, IMAGE_BYTES)),
        status().isUnauthorized(),
        "UNAUTHENTICATED",
        "listing-image-upload-unauthenticated",
        LISTING_IMAGE_UPLOAD_401);

    performError(
        upload(
            bearer(expiredAccessToken(jwtProperties)),
            imagePart("a.jpg", IMAGE_CONTENT_TYPE, IMAGE_BYTES)),
        status().isUnauthorized(),
        "TOKEN_EXPIRED",
        "listing-image-upload-token-expired",
        LISTING_IMAGE_UPLOAD_401);

    performError(
        upload(
            bearer(jwtTokenService.issueOnboardingToken(TENANT_ID)),
            imagePart("a.jpg", IMAGE_CONTENT_TYPE, IMAGE_BYTES)),
        status().isForbidden(),
        "AUTH_ONBOARDING_REQUIRED",
        "listing-image-upload-onboarding-required",
        LISTING_IMAGE_UPLOAD_403);

    // 임대인 여부는 서비스가 본다 — 세입자 토큰은 파일이 멀쩡해도 거절된다.
    performError(
        upload(tenantToken(), imagePart("a.jpg", IMAGE_CONTENT_TYPE, IMAGE_BYTES)),
        status().isForbidden(),
        "FORBIDDEN",
        "listing-image-upload-forbidden",
        LISTING_IMAGE_UPLOAD_403);

    // 빈 파일은 사진을 보내지 않은 것과 같다.
    performError(
        upload(landlordToken(), imagePart("empty.jpg", IMAGE_CONTENT_TYPE, new byte[0])),
        status().isBadRequest(),
        "LISTING_IMAGE_REQUIRED",
        "listing-image-upload-required",
        LISTING_IMAGE_UPLOAD_400);

    performError(
        upload(
            landlordToken(),
            imagePart(
                "huge.jpg",
                IMAGE_CONTENT_TYPE,
                new byte[(int) PendingListingImage.MAX_SIZE_BYTES + 1])),
        status().isPayloadTooLarge(),
        "LISTING_IMAGE_TOO_LARGE",
        "listing-image-upload-too-large",
        LISTING_IMAGE_UPLOAD_413);

    performError(
        upload(landlordToken(), imagePart("animation.gif", "image/gif", IMAGE_BYTES)),
        status().isUnsupportedMediaType(),
        "LISTING_IMAGE_UNSUPPORTED_TYPE",
        "listing-image-upload-unsupported-type",
        LISTING_IMAGE_UPLOAD_415);
  }

  /**
   * multipart 본문을 문서에서 지운다.
   *
   * <p>그대로 두면 boundary가 붙은 {@code Content-Type}과 사람이 못 읽는 바이너리가 OpenAPI 예시로 남는다. 이 오퍼레이션의 요청은 파일
   * 하나뿐이라 스키마로 보여줄 것이 없고, part 이름·형식·크기 규칙은 설명이 표로 전한다(ADR-0041).
   */
  private static OperationRequestPreprocessor hideMultipartBody() {
    return preprocessRequest(
        new ContentModifyingOperationPreprocessor((content, contentType) -> new byte[0]));
  }

  private void performError(
      MockHttpServletRequestBuilder request,
      ResultMatcher expectedStatus,
      String expectedCode,
      String identifier,
      String... errorCodes)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(expectedCode))
        .andDo(
            errorSnippet(
                identifier,
                ApiDocsTags.LISTINGS,
                LISTING_IMAGE_UPLOAD_SUMMARY,
                LISTING_IMAGE_UPLOAD_DESCRIPTION,
                errorCodes));
  }

  private MockHttpServletRequestBuilder upload(String bearerToken, MockMultipartFile file) {
    return multipart("/api/v2/listings/images")
        .file(file)
        .header(HttpHeaders.AUTHORIZATION, bearerToken);
  }

  private static MockMultipartFile imagePart(String fileName, String contentType, byte[] content) {
    return new MockMultipartFile("file", fileName, contentType, content);
  }

  private String landlordToken() {
    return bearer(jwtTokenService.issueAccessToken(LANDLORD_ID));
  }

  private String tenantToken() {
    return bearer(jwtTokenService.issueAccessToken(TENANT_ID));
  }
}
