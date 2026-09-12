# Build the jar inside the image so the host only needs Docker.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Warm the dependency cache before the sources are copied, so a code change does
# not invalidate the download layer.
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# The configuration itself, not baked into the jar: it is data this server reads, and
# copying it separately means a config change rebuilds one small layer rather than the
# application. Mount over it to edit without rebuilding at all.
COPY config-repo ./config-repo
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
