FROM maven:3.8.5-jdk-8
RUN mkdir -p /app
WORKDIR /app
COPY pom.xml /app/
COPY src /app/src
RUN ["mvn", "package"]

FROM resurfaceio/alpine-jdk11
COPY --from=0 /app/target/*.jar ./
CMD ["java", "-jar", "logger-aws-1.0-SNAPSHOT-jar-with-dependencies.jar"]
