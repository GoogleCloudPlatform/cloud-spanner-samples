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

-- IMPORTANT: Before copying this into Spanner Studio, replace YOUR_PROJECT_ID with the ID of the Google Cloud project you are using to run this sample

-- 1. Create a text embedding model
CREATE OR REPLACE MODEL TextEmbeddingModel
INPUT(content STRING(MAX))
OUTPUT(embeddings STRUCT<values ARRAY<FLOAT32>>)
REMOTE OPTIONS(
  endpoint = '//aiplatform.googleapis.com/projects/YOUR_PROJECT_ID/locations/us-central1/publishers/google/models/text-embedding-005'
);

-- 2. Generate and insert vector embeddings
UPDATE Appointments
SET DoctorNotesEmbedding = (
  SELECT embeddings.values
  FROM ML.PREDICT(
    MODEL TextEmbeddingModel,
    (SELECT DoctorNotes AS content)
  )
)
WHERE DoctorNotes IS NOT NULL AND DoctorNotesEmbedding IS NULL;