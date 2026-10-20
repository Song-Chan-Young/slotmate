# SlotMate 앱 이미지 — 빌드 단계에서 jar 만들고 실행 단계는 JRE 만
# 테스트는 MariaDB 가 필요해 이미지 빌드에서 빼고 CI(.github/workflows/ci.yml)에서 돌림
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN adduser -S -u 1001 slotmate
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
USER slotmate
EXPOSE 8080
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-jar", "app.jar"]
