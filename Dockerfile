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
ADD test_map /server/test_map
WORKDIR /server
COPY --from=build /work/build/libs/Server-*-all.jar /server/server.jar
CMD ["java", "-jar", "/server/server.jar"]