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
-- 06_ann_scann_queries.sql
-- Enterprise Multimodal Vector Search in Google Cloud Spanner
-- Approach 2: Approximate Nearest Neighbor (ANN) ScaNN Index Searches
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- QUERY PATTERN 1: Catalog Image-to-Image ANN Search
-- Golden Rules Enforced:
--  1. LIMIT 1 in target CTE ensures MaxCard = 1 for optimizer proof.
--  2. UNNEST(ARRAY(SELECT AS STRUCT ...)) isolates vector scan so Spanner does
--     not push outer WHERE (m.product_id <> 'prod_001') before ORDER BY.
-- ------------------------------------------------------------------------------
WITH target AS (
  SELECT image_embedding 
  FROM Products 
  WHERE product_id = 'prod_001' 
  LIMIT 1
)
SELECT m.product_id, m.name, m.list_price, m.distance
FROM UNNEST(ARRAY(
  SELECT AS STRUCT
    p.product_id, 
    p.name, 
    p.list_price, 
    APPROX_COSINE_DISTANCE(p.image_embedding, target.image_embedding, OPTIONS => JSON '{"num_leaves_to_search": 20}') AS distance
  FROM Products @{force_index=ProductsVectorIndex} AS p, target
  WHERE p.image_embedding IS NOT NULL
  ORDER BY distance ASC
  LIMIT 6
)) AS m
WHERE m.product_id <> 'prod_001'
ORDER BY m.distance ASC
LIMIT 5;

-- ------------------------------------------------------------------------------
-- QUERY PATTERN 2: Ad-Hoc Uploaded GCS Image ANN Search
-- Vector index-only scan (p.image_gcs_uri is stored directly in ProductsVectorIndex).
-- Replace ${BUCKET_NAME} with your actual GCS bucket name.
-- ------------------------------------------------------------------------------
WITH query_emb AS (
  SELECT p_emb.imageEmbedding
  FROM ML.PREDICT(
    MODEL ImageEmbeddings,
    (SELECT STRUCT('gs://${BUCKET_NAME}/query-images/sample_query_bed.png' AS gcsUri) AS image)
  ) AS p_emb
  LIMIT 1
)
SELECT 
  p.product_id, 
  p.name, 
  p.list_price, 
  p.image_gcs_uri,
  APPROX_COSINE_DISTANCE(p.image_embedding, query_emb.imageEmbedding, OPTIONS => JSON '{"num_leaves_to_search": 20}') AS distance
FROM Products @{force_index=ProductsVectorIndex} AS p, query_emb
WHERE p.image_embedding IS NOT NULL
ORDER BY distance ASC
LIMIT 5;

-- ------------------------------------------------------------------------------
-- QUERY PATTERN 3: Hybrid Cross-Modal Text-to-Image ANN Search
-- Materialization boundary isolates the ANN index scan to retrieve top candidates,
-- followed by point-lookup joins on Brands and interleaved Inventory.
-- ------------------------------------------------------------------------------
WITH query_emb AS (
  SELECT p_emb.textEmbedding
  FROM ML.PREDICT(
    MODEL TextMultimodalEmbeddings,
    (SELECT 'comfortable casual cropped pants' AS text)
  ) AS p_emb
  LIMIT 1
)
SELECT 
  m.product_id, 
  m.name, 
  b.brand_name, 
  b.tier,
  m.list_price, 
  inv.stock_count,
  m.distance
FROM UNNEST(ARRAY(
  SELECT AS STRUCT
    p.product_id, 
    p.brand_id,
    p.name, 
    p.list_price, 
    APPROX_COSINE_DISTANCE(p.image_embedding, query_emb.textEmbedding, OPTIONS => JSON '{"num_leaves_to_search": 20}') AS distance
  FROM Products @{force_index=ProductsVectorIndex} AS p, query_emb
  WHERE p.image_embedding IS NOT NULL
  ORDER BY distance ASC
  LIMIT 10
)) AS m
JOIN Brands AS b ON m.brand_id = b.brand_id
JOIN Inventory AS inv ON m.product_id = inv.product_id
WHERE inv.store_id = 'store_nyc'
  AND inv.stock_count > 0
ORDER BY m.distance ASC
LIMIT 5;

-- ------------------------------------------------------------------------------
-- VERIFY EXECUTION PLAN (EXPLAIN / PLAN MODE)
-- Confirms that Spanner routes the query via VectorIndexScan without base table back-joins.
-- ------------------------------------------------------------------------------
-- Execute with --query-mode=PLAN in gcloud or EXPLAIN in Spanner Studio:
WITH target AS (
  SELECT image_embedding 
  FROM Products 
  WHERE product_id = 'prod_001' 
  LIMIT 1
)
SELECT m.product_id, m.name, m.distance
FROM UNNEST(ARRAY(
  SELECT AS STRUCT
    p.product_id, 
    p.name, 
    APPROX_COSINE_DISTANCE(p.image_embedding, target.image_embedding, OPTIONS => JSON '{"num_leaves_to_search": 20}') AS distance
  FROM Products @{force_index=ProductsVectorIndex} AS p, target
  WHERE p.image_embedding IS NOT NULL
  ORDER BY distance ASC
  LIMIT 6
)) AS m
WHERE m.product_id <> 'prod_001'
ORDER BY m.distance ASC
LIMIT 5;
