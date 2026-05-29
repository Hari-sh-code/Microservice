# ── API-Gateway/Dockerfile ────────────────────────────────────────────
# Place this file at:  API-Gateway/Dockerfile

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/API-Gateway-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
