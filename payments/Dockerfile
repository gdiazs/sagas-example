FROM maven:3.9.16-amazoncorretto-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY events/pom.xml events/pom.xml
COPY payments/pom.xml payments/pom.xml
COPY orders/pom.xml orders/pom.xml
RUN mvn -B -q -pl payments dependency:go-offline

COPY payments/src payments/src
RUN mvn -B -pl payments package

FROM amazoncorretto:25-al2023
WORKDIR /app
COPY --from=build /workspace/payments/target/payments-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
