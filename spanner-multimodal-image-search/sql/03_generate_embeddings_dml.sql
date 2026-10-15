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
-- 03_generate_embeddings_dml.sql
-- Enterprise Multimodal Vector Search in Google Cloud Spanner
-- In-Database Multimodal Embedding Generation using Spanner AI & ML.PREDICT
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- OPTION A: Direct UPDATE ... SET (Simplest for Small-to-Medium Catalogs)
-- Updates only the image_embedding column in-place for all pending rows.
-- ------------------------------------------------------------------------------
UPDATE Products
SET image_embedding = (
  SELECT p.imageEmbedding
  FROM ML.PREDICT(
    MODEL ImageEmbeddings,
    (SELECT STRUCT(image_gcs_uri AS gcsUri) AS image)
  ) AS p
)
WHERE image_embedding IS NULL;

-- ------------------------------------------------------------------------------
-- OPTION B: Batched INSERT OR UPDATE INTO ... SELECT (Recommended for Large Tables)
-- Cloud Spanner's UPDATE does not support LIMIT. Feeding a SELECT ... LIMIT 20
-- into ML.PREDICT allows safe, controlled chunking without transaction timeouts.
-- Run this statement repeatedly until pending_embeddings = 0.
-- ------------------------------------------------------------------------------
/*
INSERT OR UPDATE INTO Products (
  product_id,
  brand_id,
  category_id,
  name,
  description,
  color,
  list_price,
  image_gcs_uri,
  image_embedding
)
SELECT
  product_id,
  brand_id,
  category_id,
  name,
  description,
  color,
  list_price,
  image_gcs_uri,
  imageEmbedding AS image_embedding
FROM ML.PREDICT(
  MODEL ImageEmbeddings,
  (
    SELECT
      product_id,
      brand_id,
      category_id,
      name,
      description,
      color,
      list_price,
      image_gcs_uri,
      STRUCT(image_gcs_uri AS gcsUri) AS image
    FROM Products
    WHERE image_embedding IS NULL
    LIMIT 20
  )
);
*/

-- ------------------------------------------------------------------------------
-- VERIFICATION QUERIES
-- ------------------------------------------------------------------------------
-- 1. Check pending rows (should return 0 when complete)
SELECT COUNT(*) AS pending_embeddings
FROM Products
WHERE image_embedding IS NULL;

-- 2. Verify dimension length (should return exactly 1408 dimensions)
SELECT
  product_id,
  name,
  color,
  ARRAY_LENGTH(image_embedding) AS vector_dimensions
FROM Products
WHERE image_embedding IS NOT NULL
LIMIT 5;
