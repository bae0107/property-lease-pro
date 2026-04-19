#FROM myorg/jdk17-maven:base AS builder
#
#WORKDIR /app
#COPY pom.xml .
#COPY src ./src
#
#RUN mvn -T 1C clean package -DskipTests
#
#FROM eclipse-temurin:17-jre-alpine
#WORKDIR /app
#COPY --from=builder /app/target/billing-service.jar app.jar
#
#EXPOSE 8082
#ENTRYPOINT ["java","-jar","app.jar"]


FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
