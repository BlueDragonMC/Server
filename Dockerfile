# syntax = docker/dockerfile:1.2
# This Dockerfile must be run with BuildKit enabled
# see https://docs.docker.com/engine/reference/builder/#buildkit
# for the Dockerfile that is run on our CI/CD pipeline, see production.Dockerfile

# Build the project into an executable JAR
FROM gradle:jdk21 as build
# Copy build files and source code
COPY . /work
WORKDIR /work
# Run gradle in the /work directory
RUN --mount=target=/home/gradle/.gradle,type=cache \
    /usr/bin/gradle --console=plain --info --stacktrace --no-daemon -x test --build-cache build

# Run the built JAR and expose port 25565
FROM eclipse-temurin:21-jre-alpine
EXPOSE 25565
EXPOSE 50051
WORKDIR /server

LABEL com.bluedragonmc.image=server
LABEL com.bluedragonmc.environment=development

# Copy config files and assets
COPY favicon_64.png /server/favicon_64.png

# Copy the built JAR from the previous step
COPY --from=build /work/build/libs/Server-*-all.jar /server/server.jar

# Run the server
CMD ["java", "-jar", "/server/server.jar"]