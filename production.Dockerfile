# syntax = docker/dockerfile:1
# This Dockerfile runs on the CI/CD pipeline when the Server is being deployed.
# It is much slower because `RUN mount=type=cache` is not supported by BuildKit,
# Buildah, or Kaniko in a Kubernetes cluster (docker-in-docker environment)

# Build the project into an executable JAR
FROM gradle:7.4.2-jdk17-alpine as build
# Copy build files and source code
COPY . /work
WORKDIR /work
# Run gradle in the /work directory
RUN /usr/bin/gradle --console=plain --info --stacktrace --no-daemon :jar

# Run the built JAR and expose port 25565
FROM eclipse-temurin:17-jre-alpine
EXPOSE 25565
WORKDIR /server

COPY favicon_64.png /server/favicon_64.png

# Copy the built JAR from the previous step
COPY --from=build /work/build/libs/Server-*-all.jar /server/server.jar

# Run the server
CMD ["java", "-jar", "/server/server.jar"]