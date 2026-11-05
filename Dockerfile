# Runtime image for the Spring Boot jar built by Maven in the "build-test" pipeline stage.
# Build locally with:  mvn -B package && docker build -t cicd-demo:local .
FROM eclipse-temurin:17-jre-alpine

# Run as a fixed, non-root UID so Kubernetes "runAsNonRoot" can verify it
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

WORKDIR /app
COPY target/cicd-demo.jar app.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
