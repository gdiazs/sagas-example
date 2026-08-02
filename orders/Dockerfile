FROM maven:3.9.16-amazoncorretto-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY events/pom.xml events/pom.xml
COPY payments/pom.xml payments/pom.xml
COPY orders/pom.xml orders/pom.xml
RUN mvn -B -q -pl orders dependency:go-offline

COPY orders/src orders/src
RUN mvn -B -pl orders package

FROM amazoncorretto:25-al2023
WORKDIR /app
COPY --from=build /workspace/orders/target/orders-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
