# The SaathChalo API as a container. Built by deploy/docker-compose.yml (see docs/DEPLOYMENT.md).
# Tests are not run here: they run on every push in GitHub Actions (.github/workflows/ci.yml) and before each release.

FROM eclipse-temurin:25-jdk-noble AS build
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/backend-*.jar /app.jar

FROM eclipse-temurin:25-jre-noble
# Run as an ordinary user, never as root. Uploads live in a volume mounted at /data/uploads.
RUN useradd --system --uid 10001 --home-dir /app saathchalo && mkdir -p /app /data/uploads && chown saathchalo /data/uploads
COPY --from=build /app.jar /app/app.jar
USER saathchalo
WORKDIR /app
ENV STORAGE_DIR=/data/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
