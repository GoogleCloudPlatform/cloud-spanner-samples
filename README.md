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

2. Bring up the FinAppServer hosting a grpc service from the `cloud-spanner-samples/server` directory in a separate terminal.

    ```
    $ mvn clean compile assembly:single
    $ java -jar target/server-1.0-SNAPSHOT-jar-with-dependencies.jar \
        --spanner_project_id=test-project --spanner_instance_id=test-instance \
        --spanner_database_id=test-database
    ```
> To run the application using the JDBC implementation instead of the default Java client implementation, add the flag.
> ```
> $ java -jar target/server-1.0-SNAPSHOT-jar-with-dependencies.jar \
> --spanner_project_id=test-project --spanner_instance_id=test-instance \
> --spanner_database_id=test-database --spanner_use_jdbc
> ```

3. Call RPCs using grpc_cli.

    ```
    $ grpc_cli call localhost:8080 CreateCustomer \
        "name: 'google' address: 'amphitheatre pkwy'" --channel_creds_type=insecure
    ```

## How to run the workload generator

1. Run the application in a separate terminal.

2. In the `cloud-spanner-samples/workload` directory, run
 
    ```
    $ mvn clean compile assembly:single
    $ java -jar target/workload-1.0-SNAPSHOT-jar-with-dependencies.jar --address-name localhost --port 8080 --num-accounts 200 
    ```

## How to run the application tests

1. Set up the emulator as described in #1 above.
2. Run `mvn integration-test`.
> To run the tests using the JDBC implementation of the application instead of the Java client implementation, run `mvn integration-test -DSPANNER_USE_JDBC=true`
