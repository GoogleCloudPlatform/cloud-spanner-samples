-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- ==============================================================================
-- 02_models_ddl.sql
-- Enterprise Multimodal Vector Search in Google Cloud Spanner
-- Vertex AI Model Registration: ImageEmbeddings and TextMultimodalEmbeddings
-- Model: multimodalembedding@001 (1408 dimensions)
-- Note: Replace ${PROJECT_ID} and ${REGION} with your actual GCP Project ID and Region.
-- ==============================================================================

CREATE OR REPLACE MODEL ImageEmbeddings
INPUT (
  image STRUCT<gcsUri STRING(MAX)>
)
OUTPUT (
  imageEmbedding ARRAY<FLOAT64>
)
REMOTE OPTIONS (
  endpoint = '//aiplatform.googleapis.com/projects/${PROJECT_ID}/locations/${REGION}/publishers/google/models/multimodalembedding@001',
  default_batch_size = 1
);

CREATE OR REPLACE MODEL TextMultimodalEmbeddings
INPUT (
  text STRING(MAX)
)
OUTPUT (
  textEmbedding ARRAY<FLOAT64>
)
REMOTE OPTIONS (
  endpoint = '//aiplatform.googleapis.com/projects/${PROJECT_ID}/locations/${REGION}/publishers/google/models/multimodalembedding@001',
  default_batch_size = 1
);
