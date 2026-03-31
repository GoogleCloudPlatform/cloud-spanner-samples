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
    Timestamp TIMESTAMP NOT NULL
)
PARTITION BY DATE(Timestamp);

CREATE OR REPLACE PROPERTY GRAPH CatChatNetwork
  NODE TABLES (
    Players KEY (PlayerId)
  )
  EDGE TABLES (
    ChatLogs KEY (MessageId)
      SOURCE KEY (SenderId) REFERENCES Players (PlayerId)
      DESTINATION KEY (ReceiverId) REFERENCES Players (PlayerId)
      LABEL Communicates
  );
