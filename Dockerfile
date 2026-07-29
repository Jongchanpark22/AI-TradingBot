# ── 1단계: Gradle 빌드 ──────────────────────────────────────────────
FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /app

# 의존성 캐싱 (소스 변경 시 재다운로드 방지)
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon -q

# 소스 빌드 (테스트 제외 — CI에서 별도 실행)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ── 2단계: 실행 이미지 ────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
