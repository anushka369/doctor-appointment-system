FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV SERVER_PORT=8080 \
    CLINIC_H2_TCP_ENABLED=false \
    SPRING_H2_CONSOLE_ENABLED=false
EXPOSE 8080
CMD ["sh","-c","java -Dserver.port=${PORT:-8080} -jar app.jar"]
