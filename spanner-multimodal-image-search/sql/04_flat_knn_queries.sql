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
-- 04_flat_knn_queries.sql
-- Enterprise Multimodal Vector Search in Google Cloud Spanner
-- Approach 1: Exact Vector Similarity Search (Flat K-NN with COSINE_DISTANCE)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- QUERY PATTERN 1: Catalog Image-to-Image Similarity Search
-- Given an existing catalog item (prod_001), find top 5 visually similar products.
-- ------------------------------------------------------------------------------
SELECT 
  p.product_id, 
  p.name, 
  p.list_price, 
  COSINE_DISTANCE(p.image_embedding, target.image_embedding) AS distance
FROM Products AS p,
(SELECT image_embedding FROM Products WHERE product_id = 'prod_001') AS target
WHERE p.product_id <> 'prod_001' AND p.image_embedding IS NOT NULL
ORDER BY distance ASC
LIMIT 5;

-- ------------------------------------------------------------------------------
-- QUERY PATTERN 2: Ad-Hoc Uploaded GCS Image Similarity Search
-- Computes vector for an external image via ML.PREDICT and searches the catalog.
-- Replace ${BUCKET_NAME} with your actual GCS bucket name.
-- ------------------------------------------------------------------------------
SELECT 
  p.product_id, 
  p.name, 
  p.list_price, 
  p.image_gcs_uri,
  COSINE_DISTANCE(p.image_embedding, query_emb.imageEmbedding) AS distance
FROM Products AS p
CROSS JOIN (
  SELECT p_emb.imageEmbedding
  FROM ML.PREDICT(
    MODEL ImageEmbeddings,
    (SELECT STRUCT('gs://${BUCKET_NAME}/query-images/sample_query_bed.png' AS gcsUri) AS image)
  ) AS p_emb
) AS query_emb
WHERE p.image_embedding IS NOT NULL
ORDER BY distance ASC
LIMIT 5;

-- ------------------------------------------------------------------------------
-- QUERY PATTERN 3: Hybrid Cross-Modal Text-to-Image Search
-- Natural language prompt ("comfortable casual cropped pants") converted to vector
-- on-the-fly and joined with relational Brands & Inventory tables.
-- ------------------------------------------------------------------------------
SELECT 
  p.product_id, 
  p.name, 
  b.brand_name, 
  b.tier,
  p.list_price, 
  inv.stock_count,
  COSINE_DISTANCE(p.image_embedding, query_emb.textEmbedding) AS distance
FROM Products AS p
JOIN Brands AS b ON p.brand_id = b.brand_id
JOIN Inventory AS inv ON p.product_id = inv.product_id
CROSS JOIN (
  SELECT p_emb.textEmbedding
  FROM ML.PREDICT(
    MODEL TextMultimodalEmbeddings,
    (SELECT 'comfortable casual cropped pants' AS text)
  ) AS p_emb
) AS query_emb
WHERE inv.store_id = 'store_nyc'
  AND inv.stock_count > 0 
  AND p.image_embedding IS NOT NULL
ORDER BY distance ASC
LIMIT 5;
