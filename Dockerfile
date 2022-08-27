# syntax = docker/dockerfile:1.2
# This Dockerfile must be run with BuildKit enabled
# see https://docs.docker.com/engine/reference/builder/#buildkit
# for the Dockerfile that is run on our CI/CD pipeline, see production.Dockerfile

# Build the project into an executable JAR
FROM gradle:jdk17 as build
# Copy build files and source code
COPY . /work
WORKDIR /work
# Run gradle in the /work directory
RUN --mount=target=/home/gradle/.gradle,type=cache \
    /usr/bin/gradle --console=rich --warn --stacktrace --no-daemon --build-cache --configuration-cache build

# Run the built JAR and expose port 25565
FROM eclipse-temurin:17
EXPOSE 25565
WORKDIR /server

ARG MC_MONITOR_VERSION="0.10.6"

# Add mc-monitor for container healthchecks
ADD https://github.com/itzg/mc-monitor/releases/download/$MC_MONITOR_VERSION/mc-monitor_${MC_MONITOR_VERSION}_linux_amd64.tar.gz /tmp/mc-monitor.tgz
RUN tar -xf /tmp/mc-monitor.tgz -C /usr/local/bin mc-monitor && rm /tmp/mc-monitor.tgz

HEALTHCHECK --start-period=10s --interval=5s --retries=4 CMD mc-monitor status --host localhost --port 25565

# Copy the built JAR from the previous step
COPY --from=build /work/build/libs/Server-*-all.jar /server/server.jar
# Copy config files and assets
COPY favicon_64.png /server/favicon_64.png
COPY extensions/UnifiedMetrics /server/extensions/UnifiedMetrics

# Run the server
CMD ["java", "-jar", "/server/server.jar"]