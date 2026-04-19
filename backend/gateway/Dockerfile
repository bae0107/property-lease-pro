## ---- Build Stage ----
#FROM myorg/jdk17-maven:base AS builder
#
#WORKDIR /app
#COPY pom.xml .
#COPY src ./src
#
#RUN mvn -T 1C clean package -DskipTests
#
## ---- Run Stage ----
#FROM eclipse-temurin:17-jre-alpine
#
#WORKDIR /app
#
#COPY --from=builder /app/target/gateway-service.jar app.jar
#
#EXPOSE 8080
#
#ENTRYPOINT ["java","-jar","app.jar"]


FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
