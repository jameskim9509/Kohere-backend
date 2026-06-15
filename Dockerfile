# syntax=docker/dockerfile:1
# Kohere 백엔드 앱 이미지 (M0-C). 로컬 docker-compose·클라우드(M7) 공통 동일 이미지.
# build 스테이지에서 Gradle 래퍼로 bootJar 를 만들고, runtime 스테이지는 JRE 21 로 실행한다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
# Windows 체크아웃(CRLF) 대비 줄바꿈 정리 + 실행 권한 후 bootJar (테스트는 이미지 빌드에서 제외)
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
