# --- Build stage: compile and package the application into a jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies first, then build the app.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage: a slim JRE running the packaged jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/transfer-service-0.1.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
