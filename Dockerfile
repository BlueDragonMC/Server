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
    /usr/bin/gradle --console=plain --info --stacktrace --no-daemon --build-cache --configuration-cache build

# Run the built JAR and expose port 25565
FROM eclipse-temurin:17
EXPOSE 25565
WORKDIR /server

# Copy the built JAR from the previous step
COPY --from=build /work/build/libs/Server-*-all.jar /server/server.jar
# Copy config files and assets
COPY favicon_64.png /server/favicon_64.png

# Run the server
CMD ["java", "-jar", "/server/server.jar"]