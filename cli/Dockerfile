FROM eclipse-temurin:21-jre-alpine

ARG JAR_FILE=cli/build/libs/*.jar

COPY ${JAR_FILE} /santa-cli.jar

ENTRYPOINT ["java", "-jar", "/santa-cli.jar"]
