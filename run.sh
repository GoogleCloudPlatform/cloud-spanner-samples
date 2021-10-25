#!/bin/bash
#
# Copyright 2021 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# A convenience script to help developers easily set up emulator, finapp server
# and workload.

exit_with_help_msg() {
  echo "Invalid command specified. Usage examples:
  $ bash run.sh emulator
  $ bash run.sh server java --spanner_project_id=test-project \
      --spanner_instance_id=test-instance --spanner_database_id=test-database
  $ bash run.sh server jdbc --spanner_project_id=test-project \
      --spanner_instance_id=test-instance --spanner_database_id=test-database
  $ bash run.sh workload \
       --address-name localhost --port 8080 --thread-count 200
  $ bash run.sh server python
  $ bash run.sh gen_py_proto_srcs
  " >&2
  exit 1
}

teardown_previous_emulator() {
  # There doesn't seem to be a convenient gcloud command such as:
  # gcloud emulators spanner stop. So we kill the processes on the ports.
  echo "Tearing down any emulator processes running previously."
  fuser -k 9010/tcp
  fuser -k 9020/tcp
}

run_and_configure_emulator() {
  teardown_previous_emulator
  gcloud emulators spanner start &
  if ! gcloud config configurations create emulator; then
    # If this isn't the first time this script is being executed,
    # create emulator will fail. Just activate the emulator in that case.
    gcloud config configurations activate emulator
  fi
  gcloud config set auth/disable_credentials true
  gcloud config set project test-project
  gcloud config set api_endpoint_overrides/spanner http://localhost:9020/
  gcloud spanner instances create test-instance \
    --config=emulator-config --description="Test Instance" --nodes=1
  gcloud config set spanner/instance test-instance
  gcloud spanner databases create test-database \
    --ddl-file server/src/main/java/com/google/finapp/schema.sdl
  export SPANNER_EMULATOR_HOST="localhost:9010"
}

generate_py_proto_srcs() {
  local python_source=server/src/main/python
  local proto_source=server/src/main/proto
  python -m grpc_tools.protoc -I $proto_source \
    --python_out=$python_source --grpc_python_out=$python_source \
    service.proto
  protoc --proto_path=$proto_source --python_out=$python_source \
    database.proto
}

run_server_java() {
  mvn clean compile assembly:single -pl org.example:server
  java -jar server/target/server-1.0-SNAPSHOT-jar-with-dependencies.jar $@
}

run_server_jdbc() {
  mvn clean compile assembly:single -pl org.example:server
  java -jar server/target/server-1.0-SNAPSHOT-jar-with-dependencies.jar \
    --spanner_use_jdbc $@
}

run_server_python() {
  python -m venv env
  source env/bin/activate
  pip install -r server/src/main/python/requirements.txt
  export SPANNER_EMULATOR_HOST="localhost:9010"
  python server/src/main/python/server_main.py $a
}

run_server() {
  declare -A -x servers_table=(
    ['java']="run_server_java"
    ['jdbc']="run_server_jdbc"
    ['python']="run_server_python"
  )
  local servers="${!servers_table[@]}"

  if [[ $# -lt 1 ]]; then exit_with_help_msg; fi

  local server_type=${1}
  shift
  local fn_name=${servers_table[$server_type]}
  if [[ $fn_name == '' ]]; then exit_with_help_msg; fi
  if $fn_name $@; then return 0; else return 1; fi
}

run_workload() {
  mvn clean compile assembly:single -pl org.example:workload
  java -jar workload/target/workload-1.0-SNAPSHOT-jar-with-dependencies.jar $a
}

main() {
  declare -A -x command_table=(
    ['emulator']="run_and_configure_emulator"
    ['server']="run_server"
    ['workload']="run_workload"
    ['gen_py_proto_srcs']="generate_py_proto_srcs"
  )
  local commands="${!command_table[@]}"

  if [[ $# -lt 1 ]]; then exit_with_help_msg; fi

  local command=${1}
  shift
  local fn_name=${command_table[$command]}

  if [[ $fn_name == '' ]]; then exit_with_help_msg; fi
  if $fn_name $@; then return 0; else return 1; fi
}

main "$@"
