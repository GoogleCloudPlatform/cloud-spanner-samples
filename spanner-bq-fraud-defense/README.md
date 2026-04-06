# Spanner & BigQuery: Real-Time Fraud Defense Shield

This README provides setup instructions and query examples to accompany the [Spanner & BigQuery: Real-Time Fraud Defense Shield](https://codelabs.devsite.corp.google.com/codelabs/next26/spanner-bigquery-graph) codelab (Coming soon!).

## Setup Instructions

### 1. Enable APIs
Enable the necessary Google Cloud APIs in your project:
```bash
gcloud services enable spanner.googleapis.com \
    bigquery.googleapis.com \
    aiplatform.googleapis.com \
    run.googleapis.com
```

### 2. Set up BigQuery Dataset and Connection

Set your Project ID:
```bash
export PROJECT_ID=<YOUR_PROJECT_ID>
gcloud config set project $PROJECT_ID
```

Create the `game_analytics` dataset:
```bash
bq mk -d --location=US game_analytics
```

Create a connection for external resources:
```bash
bq mk --connection --location=US --project_id=$PROJECT_ID \
    --connection_type=CLOUD_RESOURCE unicorn-connection
```

Create schemas (requires `bq_schema.sql`):
```bash
bq query --use_legacy_sql=false < bq_schema.sql
```

Load demo data into BigQuery:
```bash
bq load --source_format=AVRO game_analytics.GameplayTelemetry gs://sample-data-and-media/spanner-bq-fraud-heist/GameplayTelemetry
bq load --source_format=AVRO game_analytics.AccountSignals gs://sample-data-and-media/spanner-bq-fraud-heist/AccountSignals
bq load --source_format=AVRO game_analytics.Players gs://sample-data-and-media/spanner-bq-fraud-heist/Players
bq load --source_format=AVRO game_analytics.ChatLogs gs://sample-data-and-media/spanner-bq-fraud-heist/ChatLogs
```

### 3. Set up Spanner

Create a Spanner instance:
```bash
gcloud spanner instances create game-instance \
    --config=regional-us-central1 \
    --description="Game Instance" \
    --processing-units=100 \
    --edition=ENTERPRISE
```

Create database `game-db`:
```bash
gcloud spanner databases create game-db --instance=game-instance
```

Update Spanner schema (requires `spanner_schema.sql`):
```bash
gcloud spanner databases ddl update game-db --instance=game-instance --ddl-file=spanner_schema.sql
```

Create the search index:
```bash
gcloud spanner databases ddl update game-db --instance=game-instance \
    --ddl="CREATE SEARCH INDEX AvatarSearchIndex ON Players(AvatarDescriptionTokens)"
```

Insert initial data using Spanner Studio (see codelab for query).

---

## Reverse ETL Query (Continuous Query)

> [!WARNING]
> This query requires BigQuery Continuous Queries, which requires a reservation with ENTERPRISE edition or higher.

This query acts as a reverse-ETL engine, ensuring our transactional system (Spanner) is instantly aware of anomalies detected in our analytical system (BigQuery).

```sql
EXPORT DATA
  OPTIONS (
    uri = 'https://spanner.googleapis.com/projects/<YOUR_PROJECT_ID>/instances/game-instance/databases/game-db',
    format='CLOUD_SPANNER',
    spanner_options="""{ "table": "AccountSignals" }"""
  ) AS
SELECT
  GENERATE_UUID() as SignalId,
  PlayerId,
  'SUSPICIOUS_MOVEMENT' as AlertType,
  CURRENT_TIMESTAMP() as EventTime
FROM `game_analytics.GameplayTelemetry`
WHERE
  EventType = 'player_move'
  AND (LocationX > 1000 OR LocationY > 1000);
```

---

## Graph Query Examples

### 📊 Spanner Graph Example
Find the ringleader by tracing the financial web of transactions where victims transfer to a thief, who then transfers to a boss node.

```sql
GRAPH PlayerNetwork
MATCH (victim)-[:Transfers]->(thief)-[t:Transfers]->(boss)
RETURN boss.Name AS RingLeader, COUNT(t) AS TributesReceived, SUM(t.Amount) AS TotalLoot
GROUP BY RingLeader
ORDER BY TotalLoot DESC
LIMIT 5;
```

### 📈 BigQuery Property Graph Example
Trace communication patterns between players.

```sql
SELECT *
FROM GRAPH_TABLE(game_analytics.CatChatNetwork
  MATCH (p1:Players)-[c:Communicates]->(p2:Players)
  WHERE p1.Name = 'Pixel' OR p2.Name = 'Pixel'
  RETURN
    p1.Name AS Sender,
    p2.Name AS Receiver,
    c.Message,
    p1.ProfilePictureUrl.uri AS SenderProfilePic
)
ORDER BY Message DESC;
```
