# This Dockerfile must be run with BuildKit enabled
# see https://docs.docker.com/engine/reference/builder/#buildkit

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

ARG METRICS_VERSION="0.3.6"

# Add UnifiedMetrics by Cubxity
ADD "https://github.com/Cubxity/UnifiedMetrics/releases/download/v$METRICS_VERSION/unifiedmetrics-platform-minestom-$METRICS_VERSION.jar" /server/extensions/unifiedmetrics-$METRICS_VERSION.jar
# Copy the built JAR from the previous step
COPY --from=build /work/build/libs/Server-*-all.jar /server/server.jar
# Copy config files and assets
COPY favicon_64.png /server/favicon_64.png
COPY server-entrypoint.sh /server/entrypoint.sh
COPY extensions/UnifiedMetrics /server/extensions/UnifiedMetrics

# Run the server
CMD ["sh", "/server/entrypoint.sh"]