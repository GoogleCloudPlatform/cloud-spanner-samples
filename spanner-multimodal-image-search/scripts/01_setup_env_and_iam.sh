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
# 01_setup_env_and_iam.sh
# Enterprise Multimodal Vector Search in Google Cloud Spanner
# Step 1: Configure Environment Variables, Enable APIs, & Bind Service Agent IAM
# ==============================================================================

set -euo pipefail

echo "===> Step 1: Configuring Environment Variables..."

# Fallback to current gcloud project if not explicitly set
export PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
if [[ -z "${PROJECT_ID}" || "${PROJECT_ID}" == "(unset)" ]]; then
  echo "ERROR: PROJECT_ID is not set. Please run: export PROJECT_ID=\"<your-project-id>\"" >&2
  exit 1
fi

export REGION="${REGION:-us-central1}"
export INSTANCE_ID="${INSTANCE_ID:-retail-spanner-instance}"
export DATABASE_ID="${DATABASE_ID:-retail-vision-db}"
export BUCKET_NAME="${BUCKET_NAME:-${PROJECT_ID}-retail-multimodal-media}"

echo "Configuration Summary:"
echo "  PROJECT_ID  : ${PROJECT_ID}"
echo "  REGION      : ${REGION}"
echo "  INSTANCE_ID : ${INSTANCE_ID}"
echo "  DATABASE_ID : ${DATABASE_ID}"
echo "  BUCKET_NAME : ${BUCKET_NAME}"
echo ""

echo "===> Setting gcloud project and compute region..."
gcloud config set project "${PROJECT_ID}"
gcloud config set compute/region "${REGION}"

echo "===> Enabling required Google Cloud APIs..."
gcloud services enable \
    spanner.googleapis.com \
    aiplatform.googleapis.com \
    storage.googleapis.com

echo "===> Explicitly provisioning Service Agent identities..."
gcloud beta services identity create --service=spanner.googleapis.com --project="${PROJECT_ID}" || true
gcloud beta services identity create --service=aiplatform.googleapis.com --project="${PROJECT_ID}" || true

export PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
export SPANNER_SERVICE_AGENT="service-${PROJECT_NUMBER}@gcp-sa-spanner.iam.gserviceaccount.com"
export VERTEX_SERVICE_AGENT="service-${PROJECT_NUMBER}@gcp-sa-aiplatform.iam.gserviceaccount.com"

echo "Service Agents:"
echo "  Spanner Service Agent  : ${SPANNER_SERVICE_AGENT}"
echo "  Vertex AI Service Agent: ${VERTEX_SERVICE_AGENT}"
echo ""

echo "===> Binding IAM roles (--condition=None ensures unconditional assignment)..."
# 1. Allow Spanner to call Vertex AI models
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SPANNER_SERVICE_AGENT}" \
    --role="roles/aiplatform.user" \
    --condition=None

# 2. Allow Spanner to read media assets from Cloud Storage
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SPANNER_SERVICE_AGENT}" \
    --role="roles/storage.objectViewer" \
    --condition=None

# 3. Allow Vertex AI to read media assets from Cloud Storage
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${VERTEX_SERVICE_AGENT}" \
    --role="roles/storage.objectViewer" \
    --condition=None

echo ""
echo "===> Environment and IAM setup complete!"
echo "Next step: Run ./scripts/02_create_spanner_and_gcs.sh"
