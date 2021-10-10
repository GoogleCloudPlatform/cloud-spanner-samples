#!/bin/bash

# Usage examples:
# $ bash server/run_server.sh finapp python
# Other options for finapp are jdbc and java.
#
# To regenerate python proto srcs after changing definitions
# $ bash server/run_server.sh py_genprotosrc

function run_emulator() {
  gcloud config configurations activate emulator
  gcloud config set auth/disable_credentials true
  gcloud config set project test-project
  gcloud config set api_endpoint_overrides/spanner http://localhost:9020/
  gcloud emulators spanner start &
  gcloud spanner instances create test-instance \
    --config=emulator-config --description="Test Instance" --nodes=1
  gcloud config set spanner/instance test-instance
  gcloud spanner databases create test-database \
    --ddl-file server/src/main/java/com/google/finapp/schema.sdl
}

function py_genprotosrc() {
  PYTHON_SOURCE=server/src/main/python
  PROTO_SOURCE=server/src/main/proto
  python -m grpc_tools.protoc -I $PROTO_SOURCE \
    --python_out=$PYTHON_SOURCE --grpc_python_out=$PYTHON_SOURCE \
    service.proto
  protoc --proto_path=$PROTO_SOURCE --python_out=$PYTHON_SOURCE \
    database.proto
}

function finapp() {
  run_emulator
  if [ $1 == "java" ] || [ $1 == "jdbc" ]; then
    mvn clean compile assembly:single
    if [ $1 == "jdbc" ]; then use_jdbc=true; else use_jdbc=false; fi
    java -jar target/server-1.0-SNAPSHOT-jar-with-dependencies.jar \
      --spanner_project_id=test-project --spanner_instance_id=test-instance \
      --spanner_database_id=test-database --spanner_use_jdbc=$use_jdbc
  elif [ $1 == "python" ]; then
    python -m venv env
    source env/bin/activate
    pip install -r server/src/main/python/requirements.txt
    export SPANNER_EMULATOR_HOST="localhost:9010"
    export GOOGLE_CLOUD_PROJECT="test-project"
    python server/src/main/python/server_main.py
  fi
}

"$@"
