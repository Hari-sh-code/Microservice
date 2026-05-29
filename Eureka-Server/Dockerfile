# ── Eureka-Server/Dockerfile ──────────────────────────────────────────
# Place this file at:  Eureka-Server/Dockerfile

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Gradle puts the JAR here after:  ./gradlew bootJar
COPY build/libs/Eureka-Server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
