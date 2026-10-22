FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY apps apps
COPY libs libs
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :apps:worker:bootJar --no-daemon

FROM eclipse-temurin:17-jre

ARG TARGETARCH
ARG YT_DLP_VERSION=2026.08.19
ARG DENO_VERSION=2.9.7

RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates curl ffmpeg unzip \
    && case "${TARGETARCH}" in \
         amd64) \
           YT_DLP_ASSET=yt-dlp_linux; \
           YT_DLP_SHA256=58162f9bfdc27458ea47bfcb311cf47028f17d8154a8bf7d689861d46399230a; \
           DENO_TARGET=x86_64-unknown-linux-gnu; \
           DENO_SHA256=c6527f24f4b16031d3ae4fa9f658d5f11534c8d84ce7dc8502420280919c3490 \
           ;; \
         arm64) \
           YT_DLP_ASSET=yt-dlp_linux_aarch64; \
           YT_DLP_SHA256=b16e4dab368a816cd05d477d698a605a6ae87ccee1c8ffd38fa21d7254141fcc; \
           DENO_TARGET=aarch64-unknown-linux-gnu; \
           DENO_SHA256=c832298b1ad4422481334855f6003e0f54145762c5a134f20a489511d2f65bbf \
           ;; \
         *) echo "Unsupported architecture: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    && curl --fail --show-error --location \
         "https://github.com/yt-dlp/yt-dlp/releases/download/${YT_DLP_VERSION}/${YT_DLP_ASSET}" \
         --output /usr/local/bin/yt-dlp \
    && echo "${YT_DLP_SHA256}  /usr/local/bin/yt-dlp" | sha256sum --check --strict \
    && chmod 0755 /usr/local/bin/yt-dlp \
    && curl --fail --show-error --location \
         "https://github.com/denoland/deno/releases/download/v${DENO_VERSION}/deno-${DENO_TARGET}.zip" \
         --output /tmp/deno.zip \
    && echo "${DENO_SHA256}  /tmp/deno.zip" | sha256sum --check --strict \
    && unzip -q /tmp/deno.zip -d /tmp/deno \
    && install --mode=0755 /tmp/deno/deno /usr/local/bin/deno \
    && yt-dlp --version \
    && deno --version \
    && ffmpeg -version \
    && apt-get purge --yes --auto-remove unzip \
    && rm -rf /tmp/deno /tmp/deno.zip \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --create-home musicwave \
    && install --directory --owner=musicwave --group=musicwave /var/lib/musicwave/media
USER 10001
WORKDIR /app
COPY --from=build /workspace/apps/worker/build/libs/musicwave-worker.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
