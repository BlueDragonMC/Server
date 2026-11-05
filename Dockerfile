# Build the server
FROM gradle:9.5.1-jdk25 AS build
# Copy build files and source code
COPY . /work
WORKDIR /work
# Run gradle in the /work directory
RUN --mount=target=/home/gradle/.gradle,type=cache \
    /usr/bin/gradle --console=plain --stacktrace --no-daemon -x test installThinDist

# Run the built application
FROM eclipse-temurin:25-jre-alpine
EXPOSE 25565
EXPOSE 50051
EXPOSE 8081
WORKDIR /server

LABEL com.bluedragonmc.image=server
LABEL com.bluedragonmc.environment=development

# Copy config files and assets
COPY favicon_64.png /server/favicon_64.png

# Copy dependency JARs
COPY --from=build /work/build/thin-dist/lib /server/lib
# Copy the server itself
COPY --from=build /work/build/thin-dist/Server-*.jar /server/server.jar

# Run the server
CMD ["java", "-cp", "/server/server.jar:/server/lib/*", "com.bluedragonmc.server.ServerKt"]
