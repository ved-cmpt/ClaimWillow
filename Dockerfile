# ClaimWillow production image: one container serving the React app and the /api/v1 backend.

# 1) Build the frontend with Vite
FROM node:24-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Package the Spring Boot application with the built frontend as static resources
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build/backend
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /build/frontend/dist src/main/resources/static
RUN ./mvnw -B -q -DskipTests package

# 3) Minimal runtime
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/data/attachments && chown -R app:app /app
WORKDIR /app
COPY --from=backend --chown=app:app /build/backend/target/claimwillow.jar /app/claimwillow.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    ATTACHMENT_STORAGE_MODE=local \
    ATTACHMENT_LOCAL_DIR=/app/data/attachments \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

# The JVM defaults above suit a 512 MB, fractional-CPU instance (for example a free web service).
# Override JAVA_TOOL_OPTIONS at run time to tune for larger hosts.

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${PORT} && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "UP" <&3'

ENTRYPOINT ["java", "-jar", "/app/claimwillow.jar"]
