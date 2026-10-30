# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN cp src/main/resources/application-prod.properties.example src/main/resources/application-prod.properties \
    && mvn -B clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/student-management-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
