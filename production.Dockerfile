# syntax = docker/dockerfile:1
# This Dockerfile runs on the CI/CD pipeline when the Server is being deployed.

# Build the project into an executable JAR
FROM docker.io/library/gradle:7.4.2-jdk17 as build
# Copy build files and source code
COPY . /work
WORKDIR /work
# Run gradle in the /work directory
RUN /usr/bin/gradle --console=plain --info --stacktrace --no-daemon build

# Run the built JAR and expose port 25565
FROM docker.io/library/eclipse-temurin:17-jre-alpine
EXPOSE 25565
EXPOSE 50051
WORKDIR /server

LABEL com.bluedragonmc.image=server
LABEL com.bluedragonmc.environment=production

COPY favicon_64.png /server/favicon_64.png

# Copy the built JAR from the previous step
COPY --from=build /work/build/libs/Server-*-all.jar /server/server.jar

# Run the server
CMD ["java", "-jar", "/server/server.jar"]