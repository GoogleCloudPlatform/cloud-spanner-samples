#!/usr/bin/env bash
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# ==============================================================================
# 02_create_spanner_and_gcs.sh
# Enterprise Multimodal Vector Search in Google Cloud Spanner
# Step 2: Create Spanner Instance (ENTERPRISE), Database, GCS Bucket & Media
# ==============================================================================

set -euo pipefail

export PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
export REGION="${REGION:-us-central1}"
export INSTANCE_ID="${INSTANCE_ID:-retail-spanner-instance}"
export DATABASE_ID="${DATABASE_ID:-retail-vision-db}"
export BUCKET_NAME="${BUCKET_NAME:-${PROJECT_ID}-retail-multimodal-media}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="${SCRIPT_DIR}/../sql"

echo "===> Creating Cloud Spanner Instance (${INSTANCE_ID}) with ENTERPRISE edition..."
if gcloud spanner instances describe "${INSTANCE_ID}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  echo "Spanner instance ${INSTANCE_ID} already exists."
else
  gcloud spanner instances create "${INSTANCE_ID}" \
      --project="${PROJECT_ID}" \
      --config="regional-${REGION}" \
      --description="Retail Vision Instance" \
      --edition=ENTERPRISE \
      --processing-units=100
  echo "Spanner instance created successfully."
fi

echo "===> Creating Spanner Database (${DATABASE_ID}) with GOOGLE_STANDARD_SQL dialect..."
if gcloud spanner databases describe "${DATABASE_ID}" --instance="${INSTANCE_ID}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  echo "Database ${DATABASE_ID} already exists."
else
  gcloud spanner databases create "${DATABASE_ID}" \
      --project="${PROJECT_ID}" \
      --instance="${INSTANCE_ID}" \
      --database-dialect=GOOGLE_STANDARD_SQL
  echo "Spanner database created successfully."
fi

echo "===> Creating regional Cloud Storage bucket (gs://${BUCKET_NAME})..."
if gcloud storage buckets describe "gs://${BUCKET_NAME}" >/dev/null 2>&1; then
  echo "GCS bucket gs://${BUCKET_NAME} already exists."
else
  gcloud storage buckets create "gs://${BUCKET_NAME}" --project="${PROJECT_ID}" --location="${REGION}"
  echo "GCS bucket created successfully."
fi

echo "===> Staging selective 52 product images from gs://sample-data-and-media..."
gcloud storage ls gs://sample-data-and-media/ecomm-retail/product-images-generic/*.png \
    | head -n 52 \
    | gcloud storage cp -I "gs://${BUCKET_NAME}/ecomm-retail/product-images-generic/"

echo "===> Copying catalog tables and review datasets..."
gcloud storage cp \
    gs://sample-data-and-media/ecomm-retail/ecomm_csv.zip \
    gs://sample-data-and-media/ecomm-retail/product_reviews.csv \
    "gs://${BUCKET_NAME}/ecomm-retail/"

echo "===> Staging external ad-hoc query image (sample_query_bed.png)..."
gcloud storage cp \
    "gs://${BUCKET_NAME}/ecomm-retail/product-images-generic/00003E3B9E5336685200AE85D21B4F5E.png" \
    "gs://${BUCKET_NAME}/query-images/sample_query_bed.png"

echo "===> Applying relational schema DDL from sql/01_schema_ddl.sql..."
gcloud spanner databases ddl update "${DATABASE_ID}" \
    --project="${PROJECT_ID}" \
    --instance="${INSTANCE_ID}" \
    --ddl-file="${SQL_DIR}/01_schema_ddl.sql"

echo "===> Registering Vertex AI Multimodal Embedding models from sql/02_models_ddl.sql..."
# Substitute environment variables for model endpoint location
TEMP_MODELS_DDL=$(mktemp)
sed -e "s/\${PROJECT_ID}/${PROJECT_ID}/g" \
    -e "s/\${REGION}/${REGION}/g" \
    "${SQL_DIR}/02_models_ddl.sql" > "${TEMP_MODELS_DDL}"

gcloud spanner databases ddl update "${DATABASE_ID}" \
    --project="${PROJECT_ID}" \
    --instance="${INSTANCE_ID}" \
    --ddl-file="${TEMP_MODELS_DDL}"
rm -f "${TEMP_MODELS_DDL}"

echo ""
echo "===> Infrastructure provisioning and schema initialization complete!"
echo "Next step: Ingest catalog records by running: python3 scripts/import_data.py"
