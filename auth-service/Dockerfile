FROM maven:3.9.6-eclipse-temurin-17 as build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/auth-service-0.0.1-SNAPSHOT.jar ./auth-service.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/auth-service.jar"]
