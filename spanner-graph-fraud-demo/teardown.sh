#!/usr/bin/env bash
# Copyright 2026 Google LLC
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

# Deletes the demo database. Uses the same variables as setup.sh.
#   DELETE_INSTANCE=1  Also delete the instance. Only do this if setup.sh
#                      created it and nothing else lives on it.

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
SPANNER_INSTANCE="${SPANNER_INSTANCE:-fraud-demo-instance}"
SPANNER_DATABASE="${SPANNER_DATABASE:-fraud-graph-db-v2}"

GCLOUD=(gcloud --project="${PROJECT_ID}" --quiet)
if [[ -n "${GCLOUD_ACCOUNT:-}" ]]; then
  GCLOUD+=(--account="${GCLOUD_ACCOUNT}")
fi

echo "Deleting database ${SPANNER_DATABASE} on ${SPANNER_INSTANCE}..."
"${GCLOUD[@]}" spanner databases delete "${SPANNER_DATABASE}" \
  --instance="${SPANNER_INSTANCE}" || true

if [[ "${DELETE_INSTANCE:-0}" == "1" ]]; then
  echo "Deleting instance ${SPANNER_INSTANCE}..."
  "${GCLOUD[@]}" spanner instances delete "${SPANNER_INSTANCE}"
fi
