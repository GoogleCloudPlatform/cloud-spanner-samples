# Cloud Spanner Banking Application

This repository contains a sample banking application built using Cloud Spanner.

## How to run the application locally

NOTE: Requires gcloud, mvn, grpc_cli installed.

1. Create a database locally using cloud-spanner-emulator and export spanner host
for client libraries to work.

    ```
    $ bash run.sh emulator
    ```

2. Bring up the FinAppServer hosting a grpc service.

a. Java
    ```
    $ bash run.sh server java \
        --spanner_project_id=test-project --spanner_instance_id=test-instance \
        --spanner_database_id=test-database
    ```
> To run the application using the JDBC implementation, in the command above,
substitute `java` with `jdbc`.

b. Python

    ```
    $ python -m venv env
    $ source env/bin/activate
    $ pip install -r server/src/main/python/requirements.txt
    $ export SPANNER_EMULATOR_HOST="localhost:9010"
    $ export GOOGLE_CLOUD_PROJECT="test-project"
    $ python server/src/main/python/server_main.py
    ```

    Once you are done using the server, you can exit the virtual env using deactivate

    NOTE: The generated python classes for protos are already checked in server/src/main/python.
    If you would like to re-generate them use the following commands:

    ```
    $ PYTHON_SOURCE=server/src/main/python
    $ PROTO_SOURCE=server/src/main/proto
    $ python -m grpc_tools.protoc -I $PROTO_SOURCE \
        --python_out=$PYTHON_SOURCE --grpc_python_out=$PYTHON_SOURCE \
        service.proto
    $ protoc --proto_path=$PROTO_SOURCE --python_out=$PYTHON_SOURCE \
        database.proto
    ```

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
2. Run `mvn integration-test`.
> To run the tests using the JDBC implementation of the application instead of the Java client implementation, run `mvn integration-test -DSPANNER_USE_JDBC=true`
