# Build context: backend/ (Compose: build.context: ./backend)
FROM eclipse-temurin:21-jdk-noble AS development
WORKDIR /app

FROM development AS builder
# 의존성은 빌드 파일이 바뀔 때만 다시 받습니다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --configuration runtimeClasspath --no-daemon

COPY src ./src
# 이미지 빌드는 jar만 만듭니다. 테스트는 Testcontainers(Docker)가 필요해 PR 전에 ./gradlew build로 실행합니다.
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-noble AS runner
WORKDIR /app
RUN useradd --system --no-create-home --shell /usr/sbin/nologin spring

COPY --from=builder --chown=spring:spring /app/build/libs/*.jar ./app.jar

USER spring
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
