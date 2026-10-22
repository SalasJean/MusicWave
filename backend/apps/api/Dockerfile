FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY apps apps
COPY libs libs
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :apps:api:bootJar --no-daemon

FROM eclipse-temurin:17-jre

RUN useradd --system --uid 10001 --create-home musicwave
USER 10001
WORKDIR /app
COPY --from=build /workspace/apps/api/build/libs/musicwave-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
