FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S carcare && adduser -S carcare -G carcare
WORKDIR /app
COPY --from=build /app/target/carcare-api-0.1.0-SNAPSHOT.jar app.jar
USER carcare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
