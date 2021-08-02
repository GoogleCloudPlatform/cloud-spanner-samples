# Cloud Spanner Banking Application

This repository contains a sample banking application built using Cloud Spanner.

## How to run the application locally

NOTE: Requires gcloud, mvn, grpc_cli installed.

1. Create a database locally using cloud-spanner-emulator and export spanner host
for client libraries to work.

    ```
    $ gcloud emulators spanner start
    $ gcloud config configurations create emulator
    $ gcloud config set auth/disable_credentials true
    $ gcloud config set project test-project
    $ gcloud config set api_endpoint_overrides/spanner http://localhost:9020/
    $ gcloud spanner instances create test-instance \
        --config=emulator-config --description="Test Instance" --nodes=1
    $ gcloud config set spanner/instance test-instance
    $ gcloud spanner databases create test-database \
        --ddl-file src/main/java/com/google/finapp/schema.sdl
    $ export SPANNER_EMULATOR_HOST="localhost:9010"
    ```

2. Bring up the FinAppServer hosting a grpc service.

    ```
    $ mvn clean compile assembly:single
    $ java -jar target/finapp-1.0-SNAPSHOT-jar-with-dependencies.jar \
        --spanner_project_id=test-project --spanner_instance_id=test-instance --spanner_database_id=test-database
    ```

3. Call RPCs using grpc_cli.

    ```
    $ grpc_cli call localhost:8080 CreateCustomer \
        "name: 'google' address: 'amphitheatre pkwy'"
    ```
