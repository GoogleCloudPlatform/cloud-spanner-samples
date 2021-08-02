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
    $ gcloud config set project your-project-id
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
    $ mvn verify
    $ mvn exec:java -Dexec.mainClass=com.google.finapp.ServerMain
    ```

3. Call RPCs using grpc_cli.

    ```
    $ grpc_cli call localhost:8080 CreateCustomer \
        "name: 'google' address: 'amphitheatre pkwy'"
    ```

## How to run the application tests

1. Set up the emulator as described in #1 above.
2. Replace the variables in `FinAppTests.setUpTests` according to your chosen instance, project, and database IDs.
> The default variables should work for instance and database. The project variable must be changed based on `your-project-id` from `gcloud config set project your-project-id`. If this exact command was used, use `"your-project-id"`.
3. Run `mvn test`.
