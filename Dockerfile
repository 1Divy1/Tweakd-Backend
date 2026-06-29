# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Stage 1: build the application jar
# We use a full JDK here because compiling needs the Java compiler + Maven.
# This stage is "thrown away" — none of it ends up in the final image,
# which keeps the shipped image small and free of build tooling.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy ONLY the files needed to resolve dependencies first.
# Docker caches each step; as long as pom.xml and the Maven wrapper don't
# change, the (slow) dependency download below is reused from cache even
# when you edit your source code.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# Now copy the source and build. Tests are skipped in the image build —
# run them in CI instead, so a flaky test never blocks a deploy.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/*.jar app.jar

# ---------------------------------------------------------------------------
# Stage 2: the runtime image (this is what actually gets deployed)
# Only a JRE (no compiler/Maven) — smaller image, smaller attack surface.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as a non-root user. If the app is ever compromised, the attacker
# is not root inside the container. Cloud Run requires nothing special
# here, but it's a baseline security practice.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Pull just the built jar over from the build stage.
COPY --from=build /workspace/app.jar app.jar

# Documentation only. Cloud Run ignores EXPOSE and tells the app which
# port to use via the $PORT env var (see ENTRYPOINT below).
EXPOSE 8080

# JVM flags suited to running inside a container:
#   MaxRAMPercentage=75  -> use up to 75% of the container's memory limit
#                           for the heap (leaves headroom for threads/metaspace).
# Override or extend at deploy time with the JAVA_OPTS env var.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Cloud Run injects a $PORT env var and expects the app to listen on it.
# We bind Spring's server.port to $PORT, defaulting to 8080 for local runs.
# `exec` replaces the shell with the Java process so it becomes PID 1 and
# receives SIGTERM directly — this lets Cloud Run shut the app down cleanly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
