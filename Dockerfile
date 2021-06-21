FROM openjdk:8

COPY target/finapp-1.0-SNAPSHOT-jar-with-dependencies.jar /app/finapp.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/finapp.jar"]