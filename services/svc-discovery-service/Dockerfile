FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# Build application
COPY src ./src
RUN mvn -B -DskipTests package

# Runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Default values can be overridden via --env-file
ENV SPRING_PROFILES_ACTIVE=dev \
    SERVER_PORT=8761

EXPOSE 8761
COPY --from=build /app/target/discovery-server-1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]
