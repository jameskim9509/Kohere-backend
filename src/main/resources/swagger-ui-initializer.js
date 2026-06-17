// Swagger UI 부트스트랩 — 기본 petstore 대신 우리 OpenAPI 명세를 가리킨다(ADR-0016).
// webjar 기본 swagger-initializer.js 를 빌드 시 이 파일로 대체한다(build.gradle prepareSwaggerUi).
window.onload = function () {
  window.ui = SwaggerUIBundle({
    url: "openapi3.yaml",
    dom_id: "#swagger-ui",
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: "StandaloneLayout",
  });
};
