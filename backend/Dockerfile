# Builds the NGO Connect backend into a container image that any Docker host (Render, Railway,
# Fly.io, a VPS…) can run. Two stages: the first compiles with Maven, the second keeps only
# what is needed to run, so the final image carries no Maven, no source and no build cache.

# ── Stage 1: build the jar ─────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dependencies first, on their own layer: they only re-download when pom.xml changes, so a
# code-only change rebuilds in seconds instead of re-fetching the whole internet.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are skipped here because they need a live MongoDB; run them locally with ./mvnw test.
RUN mvn -B -q package -DskipTests \
 && find target -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# ── Stage 2: run it ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# Tesseract OCR reads NGO registration documents uploaded as images. tess4j ships the native
# library for Windows only; on Linux it must come from the system, or OCR fails at runtime.
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr \
 && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user rather than root.
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /app/app.jar app.jar

# The platform tells the app which port to use through $PORT (see server.port); 8082 locally.
EXPOSE 8082
# MaxRAMPercentage sizes the heap from the container's memory limit — on a 512MB free tier the
# JVM's default would otherwise leave too little room and the container gets killed.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
