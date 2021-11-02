# Cloud Spanner Banking Application

This repository contains a sample banking application built using Cloud Spanner.

## How to run the application locally

NOTE: Requires gcloud, mvn, grpc_cli installed.

1. Create a database locally using cloud-spanner-emulator and export spanner host
for client libraries to work.

    ```
    $ mvn clean install -Dmaven.test.skip=true
    $ bash run.sh emulator
    $ export SPANNER_EMULATOR_HOST="localhost:9010"
    ```

2. Bring up the FinAppServer hosting a grpc service.

    ```
    $ bash run.sh server java \
        --spanner_project_id=test-project --spanner_instance_id=test-instance \
        --spanner_database_id=test-database
    ```
> To run the application using the JDBC implementation, in the command above,
substitute `java` with `jdbc`.

3. Call RPCs using grpc_cli.

    ```
    $ grpc_cli call localhost:8080 CreateCustomer \
        "name: 'google' address: 'amphitheatre pkwy'" --channel_creds_type=insecure
    ```

## How to run the workload generator

1. Bring up the finapp server using steps described above.

2. In a separate terminal, bring up the workload using the following command:
 
    ```
    $ bash run.sh workload \
        --address-name localhost --port 8080 --num-accounts 200 
    ```

## How to run the application tests

1. Set up the emulator as described in #1 above.
2. Run `mvn integration-test` for testing the Java client implementation, or
3. Run `mvn integration-test -DSPANNER_USE_JDBC=true` for the JDBC implementation
