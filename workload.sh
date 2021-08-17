#!/bin/bash

gcloud config set project $1
gcloud spanner instances create $2 --config=regional-us-west1 --description="$2" --nodes=1
gcloud config set spanner/instance $2
gcloud spanner databases create $3 --ddl-file server/src/main/java/com/google/finapp/schema.sdl

cd server
echo "Compiling server code"
mvn clean compile assembly:single -q
fuser -k 8080/tcp
echo "Starting server"
java -jar target/server-1.0-SNAPSHOT-jar-with-dependencies.jar --spanner_project_id=$1 --spanner_instance_id=$2 --spanner_database_id=$3 &

cd ../workload
echo "Generating workload"
mvn -DskipTests package exec:java -Dexec.mainClass=com.google.finapp.WorkloadGenerator -l mvn.log