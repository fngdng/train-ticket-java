FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/trains-service-0.0.1-SNAPSHOT.jar ./trains-service.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/trains-service.jar"]
