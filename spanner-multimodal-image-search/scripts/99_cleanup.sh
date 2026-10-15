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
# 99_cleanup.sh
# Enterprise Multimodal Vector Search in Google Cloud Spanner
# Step 99: Teardown Spanner Instance, GCS Media Bucket, and Local Artifacts
# ==============================================================================

set -euo pipefail

export PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
export INSTANCE_ID="${INSTANCE_ID:-retail-spanner-instance}"
export BUCKET_NAME="${BUCKET_NAME:-${PROJECT_ID}-retail-multimodal-media}"

echo "===================================================================="
echo "WARNING: This will permanently delete the following cloud resources:"
echo "  - Spanner Instance: ${INSTANCE_ID} (and all databases/tables/indexes)"
echo "  - Cloud Storage Bucket: gs://${BUCKET_NAME} (and all staged images)"
echo "===================================================================="
read -p "Are you sure you want to proceed with teardown? (y/N): " -r CONFIRM
if [[ ! "${CONFIRM}" =~ ^[Yy]$ ]]; then
  echo "Cleanup aborted by user."
  exit 0
fi

echo "===> Deleting Cloud Spanner instance: ${INSTANCE_ID}..."
if gcloud spanner instances describe "${INSTANCE_ID}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud spanner instances delete "${INSTANCE_ID}" --project="${PROJECT_ID}" --quiet
  echo "Spanner instance deleted successfully."
else
  echo "Spanner instance ${INSTANCE_ID} does not exist, skipping."
fi

echo "===> Deleting Cloud Storage bucket: gs://${BUCKET_NAME}..."
if gcloud storage buckets describe "gs://${BUCKET_NAME}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud storage rm --recursive "gs://${BUCKET_NAME}" --quiet
  echo "Cloud Storage bucket deleted successfully."
else
  echo "Bucket gs://${BUCKET_NAME} does not exist, skipping."
fi

echo "===> Removing local generated / temporary files..."
find . -name "*.pyc" -delete 2>/dev/null || true
find . -name "__pycache__" -delete 2>/dev/null || true

echo ""
echo "===> Cleanup complete! All cloud resources and temporary files removed."
