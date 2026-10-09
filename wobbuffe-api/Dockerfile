FROM maven:3.10.0-eclipse-temurin-25-noble AS build
WORKDIR /workspace
COPY wobbuffe-starter ./wobbuffe-starter
RUN mvn -B -f wobbuffe-starter/pom.xml -Dmaven.test.skip=true install
COPY wobbuffe-demo/wobbuffe-api ./wobbuffe-api
RUN mvn -B -f wobbuffe-api/pom.xml verify

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/wobbuffe-api/target/wobbuffe-api-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
