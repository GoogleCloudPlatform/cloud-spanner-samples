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
-- 01_schema_ddl.sql
-- Enterprise Multimodal Vector Search in Google Cloud Spanner
-- Relational Schema: Brands, ProductCategories, Products, Inventory, CustomerReviews
-- ==============================================================================

CREATE TABLE Brands (
  brand_id INT64 NOT NULL,
  brand_name STRING(100),
  tier STRING(50),
  country_of_origin STRING(50),
) PRIMARY KEY (brand_id);

CREATE TABLE ProductCategories (
  category_id INT64 NOT NULL,
  category_name STRING(100),
  department STRING(100),
) PRIMARY KEY (category_id);

CREATE TABLE Products (
  product_id STRING(36) NOT NULL,
  brand_id INT64,
  category_id INT64,
  name STRING(255),
  description STRING(MAX),
  color STRING(50),
  list_price NUMERIC,
  image_gcs_uri STRING(1024),
  image_embedding ARRAY<FLOAT64>(vector_length=>1408),
) PRIMARY KEY (product_id);

CREATE TABLE Inventory (
  product_id STRING(36) NOT NULL,
  store_id STRING(50) NOT NULL,
  stock_count INT64,
  last_restock_timestamp TIMESTAMP,
) PRIMARY KEY (product_id, store_id),
INTERLEAVE IN PARENT Products ON DELETE CASCADE;

CREATE TABLE CustomerReviews (
  product_id STRING(36) NOT NULL,
  review_id STRING(36) NOT NULL,
  rating INT64,
  review_text STRING(MAX),
  review_date DATE,
) PRIMARY KEY (product_id, review_id),
INTERLEAVE IN PARENT Products ON DELETE CASCADE;
