-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Cymbal Bank P2P fraud demo: Spanner schema (GoogleSQL dialect).
--
-- Spanner Studio captions graph nodes with their alphabetically first property,
-- so every other People column intentionally sorts after "Name".
--
-- The Gemini model used by ML.PREDICT is created by setup.sh, because its
-- endpoint includes your project ID.

-- Customers of the P2P payments app. These rows are the graph nodes.
CREATE TABLE People (
  Name                  STRING(128) NOT NULL,
  PersonId              STRING(64)  NOT NULL,
  Segment               STRING(32),
  Status                STRING(16)  NOT NULL DEFAULT ('ACTIVE'),
  ProfileBio            STRING(MAX),
  ProfilePhotoUrl       STRING(MAX),
  ProfileBioEmbedding   ARRAY<FLOAT32>(vector_length=>128),
  ProfilePhotoEmbedding ARRAY<FLOAT32>(vector_length=>128),
  SignupTime            TIMESTAMP   NOT NULL,
  -- Filled in during the demo by the PageRank graph algorithm.
  PageRank              FLOAT64
) PRIMARY KEY (PersonId);

-- Peer-to-peer money movements. These rows are the graph edges.
-- Each transfer is interleaved in (stored with) its sender's People row.
-- Graph algorithms such as PageRank require this layout. The sender column
-- must be named after the parent key, PersonId; the graph calls it SenderId.
CREATE TABLE Transfers (
  PersonId   STRING(64) NOT NULL,
  TransferId STRING(36) NOT NULL,
  ReceiverId STRING(64) NOT NULL,
  Amount     NUMERIC    NOT NULL,
  Memo       STRING(MAX),
  CreatedAt  TIMESTAMP  NOT NULL,
  -- Full-text search tokens for Memo, used by SEARCH().
  MemoTokens TOKENLIST AS (TOKENIZE_FULLTEXT(Memo)) HIDDEN,
  CONSTRAINT FK_TransferReceiver FOREIGN KEY (ReceiverId) REFERENCES People (PersonId)
) PRIMARY KEY (PersonId, TransferId),
  INTERLEAVE IN PARENT People ON DELETE CASCADE;

-- A graph only sees the columns that exist when it is created, so the token
-- column and its search index come before CREATE PROPERTY GRAPH.
CREATE SEARCH INDEX TransfersMemoIndex ON Transfers(MemoTokens);

-- Real-time risk signals. In production, a BigQuery continuous query streams
-- these into Spanner (reverse ETL) from login and device telemetry.
CREATE TABLE AccountSignals (
  SignalId   STRING(36) NOT NULL DEFAULT (GENERATE_UUID()),
  PersonId   STRING(64) NOT NULL,
  SignalType STRING(64) NOT NULL,
  Details    STRING(MAX),
  EventTime  TIMESTAMP  NOT NULL,
  CONSTRAINT FK_SignalPerson FOREIGN KEY (PersonId) REFERENCES People (PersonId)
) PRIMARY KEY (SignalId);

-- The property graph is a lightweight layer over the tables above: no data is copied.
CREATE PROPERTY GRAPH PaymentsNetwork
  NODE TABLES (
    People
      KEY (PersonId)
      LABEL Person
  )
  EDGE TABLES (
    Transfers
      KEY (PersonId, TransferId)
      SOURCE KEY (PersonId) REFERENCES People (PersonId)
      DESTINATION KEY (ReceiverId) REFERENCES People (PersonId)
      LABEL Transfers
        PROPERTIES (PersonId AS SenderId, ReceiverId, TransferId, Amount, Memo,
                    MemoTokens, CreatedAt)
  );
