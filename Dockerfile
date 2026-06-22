# syntax=docker/dockerfile:1
# Kohere 백엔드 런타임 이미지. 실행 jar는 CI(deploy.yml)에서 "테스트 포함" 빌드해 COPY한다.
#   이유: OpenAPI 스펙(Swagger)은 REST Docs 테스트 스니펫에서 생성되는데, 이미지 빌드 안에선
#         Testcontainers(실 DB)를 못 띄워 -x test가 되고 그러면 스펙이 빈다(ADR-0016 경로).
#   따라서 jar는 build/libs/*.jar 로 미리 빌드돼 있어야 한다(로컬 compose는 ./gradlew bootJar 후 --build).
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
