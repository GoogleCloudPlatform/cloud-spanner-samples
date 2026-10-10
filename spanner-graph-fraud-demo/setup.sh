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

# Creates the Cymbal Bank fraud demo database: schema, property graph, people
# (with pre-computed bio and photo embeddings), transfers, risk signals, and a
# Gemini remote model for ML.PREDICT.
# After it finishes, open Spanner Studio and run sql/demo_queries.sql.
#
# Configuration (environment variables):
#   PROJECT_ID        Google Cloud project (default: current gcloud project).
#   SPANNER_INSTANCE  Instance to use or create (default: fraud-demo-instance).
#   SPANNER_DATABASE  Database to create (default: fraud-graph-db-v2).
#   SPANNER_CONFIG    Instance config if the instance is created
#                     (default: regional-us-central1).
#   GEMINI_MODEL      Gemini model that ML.PREDICT calls
#                     (default: gemini-3.8-flash).
#   GEMINI_LOCATION   Vertex AI location of the model (default: global).
#   PHOTO_BUCKET      Cloud Storage bucket for the profile photos, created in
#                     us-central1 if needed (default: PROJECT_ID-spanner-fraud-demo).
#   GCLOUD_ACCOUNT    Optional gcloud account to run the commands as.
#   RECREATE=1        Drop and recreate the database if it already exists.

set -euo pipefail

cd "$(dirname "$0")"

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
SPANNER_INSTANCE="${SPANNER_INSTANCE:-fraud-demo-instance}"
SPANNER_DATABASE="${SPANNER_DATABASE:-fraud-graph-db-v2}"
SPANNER_CONFIG="${SPANNER_CONFIG:-regional-us-central1}"
GEMINI_MODEL="${GEMINI_MODEL:-gemini-3.8-flash}"
GEMINI_LOCATION="${GEMINI_LOCATION:-global}"
PHOTO_BUCKET="${PHOTO_BUCKET:-${PROJECT_ID}-spanner-fraud-demo}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "Set PROJECT_ID or run: gcloud config set project YOUR_PROJECT" >&2
  exit 1
fi

GCLOUD=(gcloud --project="${PROJECT_ID}" --quiet)
if [[ -n "${GCLOUD_ACCOUNT:-}" ]]; then
  GCLOUD+=(--account="${GCLOUD_ACCOUNT}")
fi

echo "Project:  ${PROJECT_ID}"
echo "Instance: ${SPANNER_INSTANCE}"
echo "Database: ${SPANNER_DATABASE}"

# Vertex AI serves the Gemini model that ML.PREDICT calls from Spanner.
# Cloud Storage holds the profile photos.
"${GCLOUD[@]}" services enable spanner.googleapis.com aiplatform.googleapis.com \
  storage.googleapis.com

# Spanner Graph needs the Enterprise or Enterprise Plus edition.
if "${GCLOUD[@]}" spanner instances describe "${SPANNER_INSTANCE}" \
    >/dev/null 2>&1; then
  echo "Using existing instance ${SPANNER_INSTANCE}."
else
  echo "Creating instance ${SPANNER_INSTANCE} (Enterprise, 100 PUs)..."
  "${GCLOUD[@]}" spanner instances create "${SPANNER_INSTANCE}" \
    --config="${SPANNER_CONFIG}" \
    --edition=ENTERPRISE \
    --processing-units=100 \
    --description="Spanner Graph fraud demo"
fi

if "${GCLOUD[@]}" spanner databases describe "${SPANNER_DATABASE}" \
    --instance="${SPANNER_INSTANCE}" >/dev/null 2>&1; then
  if [[ "${RECREATE:-0}" != "1" ]]; then
    echo "Database ${SPANNER_DATABASE} already exists." \
         "Run with RECREATE=1 to drop and rebuild it." >&2
    exit 1
  fi
  echo "Dropping existing database ${SPANNER_DATABASE}..."
  "${GCLOUD[@]}" spanner databases delete "${SPANNER_DATABASE}" \
    --instance="${SPANNER_INSTANCE}"
fi

echo "Creating database, tables and the PaymentsNetwork property graph..."
"${GCLOUD[@]}" spanner databases create "${SPANNER_DATABASE}" \
  --instance="${SPANNER_INSTANCE}" \
  --ddl-file=sql/schema.sql

# A remote model lets SQL call Gemini with ML.PREDICT. Spanner calls Vertex AI
# with its own service agent, so no extra IAM setup is needed.
echo "Creating the GeminiModel remote model (${GEMINI_MODEL})..."
"${GCLOUD[@]}" spanner databases ddl update "${SPANNER_DATABASE}" \
  --instance="${SPANNER_INSTANCE}" \
  --ddl="CREATE MODEL GeminiModel
INPUT (prompt STRING(MAX))
OUTPUT (content STRING(MAX))
REMOTE OPTIONS (
  endpoint = '//aiplatform.googleapis.com/projects/${PROJECT_ID}/locations/${GEMINI_LOCATION}/publishers/google/models/${GEMINI_MODEL}',
  default_batch_size = 1
)"

# People.ProfilePhotoUrl links to the photos in your own bucket. The links
# open in a browser for anyone with read access to the bucket.
if ! "${GCLOUD[@]}" storage buckets describe "gs://${PHOTO_BUCKET}" \
    >/dev/null 2>&1; then
  echo "Creating bucket gs://${PHOTO_BUCKET}..."
  "${GCLOUD[@]}" storage buckets create "gs://${PHOTO_BUCKET}" \
    --location=us-central1 --uniform-bucket-level-access
fi
echo "Uploading profile photos to gs://${PHOTO_BUCKET}/profile_pics/..."
"${GCLOUD[@]}" storage rsync profile_pics "gs://${PHOTO_BUCKET}/profile_pics"
PHOTO_BASE_URL="https://storage.cloud.google.com/${PHOTO_BUCKET}/profile_pics"

# Each data file holds INSERT statements that end with ";" at the end of a
# line. Run them one at a time (gcloud accepts one statement per call).
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

for file in data/01_people.sql data/02_transfers.sql data/03_signals.sql; do
  echo "Loading ${file}..."
  rm -f "${WORK_DIR}"/stmt_*
  awk -v dir="${WORK_DIR}" '
    /^--/ || /^[[:space:]]*$/ { next }
    { if (out == "") { n++; out = sprintf("%s/stmt_%04d.sql", dir, n) }
      print > out }
    /;[[:space:]]*$/ { close(out); out = "" }
  ' "${file}"
  for stmt in "${WORK_DIR}"/stmt_*; do
    "${GCLOUD[@]}" spanner databases execute-sql "${SPANNER_DATABASE}" \
      --instance="${SPANNER_INSTANCE}" \
      --sql="$(sed -e 's/;[[:space:]]*$//' \
                   -e "s|@PHOTO_BASE_URL@|${PHOTO_BASE_URL}|g" "${stmt}")" \
      >/dev/null
  done
done

echo "Row counts:"
"${GCLOUD[@]}" spanner databases execute-sql "${SPANNER_DATABASE}" \
  --instance="${SPANNER_INSTANCE}" \
  --sql="SELECT (SELECT COUNT(*) FROM People) AS people,
                (SELECT COUNT(*) FROM Transfers) AS transfers,
                (SELECT COUNT(*) FROM AccountSignals) AS signals"

cat <<EOF

Done. Open Spanner Studio:
  https://console.cloud.google.com/spanner/instances/${SPANNER_INSTANCE}/databases/${SPANNER_DATABASE}/details/query?project=${PROJECT_ID}
Then run the queries in sql/demo_queries.sql.
EOF
