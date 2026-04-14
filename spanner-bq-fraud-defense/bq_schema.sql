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

SET @@dataset_id='game_analytics';
-- BigQuery Schema

CREATE TABLE IF NOT EXISTS GameplayTelemetry (
    EventId STRING NOT NULL,
    PlayerId STRING NOT NULL,
    EventType STRING NOT NULL,
    EventTimestamp TIMESTAMP NOT NULL,
    LocationX FLOAT64,
    LocationY FLOAT64,
    ActionDetails STRING
)
PARTITION BY DATE(EventTimestamp);

CREATE TABLE IF NOT EXISTS AccountSignals (
    SignalId STRING NOT NULL,
    PlayerId STRING NOT NULL,
    AlertType STRING NOT NULL,
    EventTime TIMESTAMP NOT NULL
)
PARTITION BY DATE(EventTime);

CREATE TABLE IF NOT EXISTS Players (
    PlayerId STRING NOT NULL,
    Name STRING NOT NULL,
    Species STRING NOT NULL,
    Clan STRING,
    ProfilePictureUrl STRUCT<uri STRING, version STRING, authorizer STRING, details JSON>
);

CREATE TABLE IF NOT EXISTS ChatLogs (
    MessageId STRING NOT NULL,
    SenderId STRING NOT NULL,
    ReceiverId STRING NOT NULL,
    Message STRING NOT NULL,
    Timestamp TIMESTAMP NOT NULL,
    MessageEmbedding STRUCT<result ARRAY<FLOAT64>,
    status STRING>
)
PARTITION BY DATE(Timestamp);

  CREATE OR REPLACE PROPERTY GRAPH game_analytics.CatChatNetwork 
      NODE TABLES(Players KEY(PlayerId)) 
      EDGE TABLES( ChatLogs KEY(MessageId) SOURCE KEY(SenderId)
      REFERENCES
        Players(PlayerId) DESTINATION KEY(ReceiverId)
      REFERENCES
        Players(PlayerId) 
      LABEL Communicates 
      PROPERTIES( Message,
          Timestamp,
          MessageEmbedding));
