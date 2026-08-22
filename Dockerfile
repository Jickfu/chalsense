# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21.0.11_10-jdk-noble AS build
WORKDIR /workspace
RUN apt-get update \
    && apt-get install --yes --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
COPY . .
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress -pl chalsense-server -am -DskipTests package

FROM eclipse-temurin:21.0.11_10-jre-noble AS runtime
LABEL org.opencontainers.image.title="ChalSense Server" \
      org.opencontainers.image.description="Self-hosted human verification HTTP service" \
      org.opencontainers.image.source="https://github.com/Jickfu/chalsense" \
      org.opencontainers.image.licenses="Apache-2.0"

RUN groupadd --gid 10001 chalsense \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin chalsense \
    && mkdir -p /opt/chalsense /data/backgrounds \
    && chown -R 10001:10001 /opt/chalsense /data/backgrounds
COPY --from=build --chown=10001:10001 /workspace/chalsense-server/target/chalsense-server-*.jar /opt/chalsense/chalsense-server.jar

USER 10001:10001
WORKDIR /opt/chalsense
EXPOSE 8080 9090
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget --quiet --output-document=- http://127.0.0.1:8080/livez >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.io.tmpdir=/tmp", "-jar", "/opt/chalsense/chalsense-server.jar"]
