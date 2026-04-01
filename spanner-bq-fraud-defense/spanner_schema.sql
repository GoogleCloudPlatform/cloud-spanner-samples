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


-- Spanner Schema (GoogleSQL)

CREATE TABLE IF NOT EXISTS Players (
    PlayerId STRING(36) NOT NULL,
    Name STRING(128) NOT NULL,
    Species STRING(32) NOT NULL,
    Clan STRING(32),
    AvatarDescription STRING(MAX),
    AvatarDescriptionTokens TOKENLIST AS (TOKENIZE_FULLTEXT(AvatarDescription)) HIDDEN,
    AvatarEmbedding ARRAY<FLOAT64>(vector_length=>128),
    ProfilePictureUrl STRING(MAX),

    CreatedAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (PlayerId);

CREATE TABLE IF NOT EXISTS AccountSignals (
    SignalId STRING(36) NOT NULL DEFAULT (GENERATE_UUID()),
    PlayerId STRING(36) NOT NULL,
    AlertType STRING(64) NOT NULL,
    EventTime TIMESTAMP NOT NULL,
    CONSTRAINT FK_PlayerSignal FOREIGN KEY (PlayerId) REFERENCES Players (PlayerId)
) PRIMARY KEY (SignalId);

CREATE TABLE IF NOT EXISTS Transactions (
    TransactionId STRING(36) NOT NULL,
    SenderId STRING(36) NOT NULL,
    ReceiverId STRING(36) NOT NULL,
    Amount FLOAT64 NOT NULL,
    Timestamp TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    IsSuspicious BOOL,
    CONSTRAINT FK_Sender FOREIGN KEY (SenderId) REFERENCES Players (PlayerId),
    CONSTRAINT FK_Receiver FOREIGN KEY (ReceiverId) REFERENCES Players (PlayerId)
) PRIMARY KEY (TransactionId);

-- Graph Schema
CREATE OR REPLACE PROPERTY GRAPH PlayerNetwork
  NODE TABLES (
    Players
  )
  EDGE TABLES (
    Transactions
      SOURCE KEY (SenderId) REFERENCES Players (PlayerId)
      DESTINATION KEY (ReceiverId) REFERENCES Players (PlayerId)
      LABEL Transfers
  );

-- Search Index for "Multimodal" Description Search (Simulated via token search for now)


