FROM maven:3.8.3-openjdk-17 as build

COPY target/spanner-0.0.1-SNAPSHOT.jar spanner-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","/spanner-0.0.1-SNAPSHOT.jar"]