# Java Cloud Spanner Sample Online Banking Application

A sample online banking application that uses the Cloud Spanner advanced features, including [transactions](https://cloud.google.com/spanner/docs/transactions), [AI integration](https://cloud.google.com/spanner/docs/ml), [full-text search](https://cloud.google.com/spanner/docs/full-text-search), and [BigQuery federated queries](https://cloud.google.com/spanner/docs/databoost/databoost-run-queries).

This sample requires [Java](https://www.java.com/en/download/) and [Maven](http://maven.apache.org/) for building the application.

The accompanying codelab for this sample is: [https://codelabs.developers.google.com/spanner-online-banking-app](https://codelabs.developers.google.com/spanner-online-banking-app)

## Build and Run

ℹ️ Note that you can use [Google Cloud Shell](https://cloud.google.com/shell/docs) instead of developing locally.  In that case skip step #2 and #3 below.

1. Create a project in the [Google Cloud Platform console](https://console.cloud.google.com/).

2. Install and configure the [gcloud CLI](https://cloud.google.com/sdk/docs/install-sdk).

3. Follow the [Java development environment setup instructions](https://cloud.google.com/java/docs/setup).

4. Enable the required APIs (Spanner, Vertex AI, and BigQuery) for your project:

```bash
gcloud services enable spanner.googleapis.com
gcloud services enable aiplatform.googleapis.com
gcloud services enable bigquery.googleapis.com
gcloud services enable bigqueryconnection.googleapis.com
```

5. Create a Cloud Spanner instance:

```bash
gcloud spanner instances create "cloudspanner-onlinebanking" \
  --config=regional-us-central1 \
  --description="Spanner Online Banking" \
  --nodes=1 \
  --edition=ENTERPRISE \
  --default-backup-schedule-type=NONE
```

6. Create a BigQuery table and Spanner connection:

```bash
gcloud components install bq

bq mk --location=us-central1 MarketingCampaigns
bq mk --table MarketingCampaigns.CustomerSegments CampaignId:STRING,CampaignName:STRING,CustomerId:INT64
export GOOGLE_CLOUD_PROJECT=<PROJECT_ID>
bq mk --connection \
  --connection_type=CLOUD_SPANNER \
  --properties="{\"database\": \"projects/$GOOGLE_CLOUD_PROJECT/instances/cloudspanner-onlinebanking/databases/onlinebanking\", \"useParallelism\": true, \"useDataBoost\": true}" \
  --location=us-central1 \
  spanner-connection

bq query --use_legacy_sql=false '
INSERT INTO MarketingCampaigns.CustomerSegments (CampaignId, CampaignName, CustomerId)
VALUES
  ("campaign1", "Spring Promotion", 1),
  ("campaign1", "Spring Promotion", 3),
  ("campaign1", "Spring Promotion", 5),
  ("campaign1", "Spring Promotion", 7),
  ("campaign1", "Spring Promotion", 9),
  ("campaign1", "Spring Promotion", 11)
'
```

7. Build the application using the following Maven command:

```bash
mvn package
```

8. Set the instance and database id via environment variables:

```bash
export SPANNER_INSTANCE=cloudspanner-onlinebanking
export SPANNER_DATABASE=onlinebanking
```

9. Run the online banking application to display the available commands:

```bash
java -jar target/onlinebanking.jar
```

Command output:

```bash
Online Banking Application 1.0.0
Usage:
  java -jar target/onlinebanking.jar <command> [command_option(s)]

Examples:
  java -jar target/onlinebanking.jar create
      - Create a sample Cloud Spanner database and schema in your project.

  java -jar target/onlinebanking.jar insert
      - Insert sample Customers, Accounts, and Transactions into the database.

  java -jar target/onlinebanking.jar categorize
      - Use AI to categorize transactions in the database.

  java -jar target/onlinebanking.jar query balance 1
      - Query customer account balance(s) by customer id.

  java -jar target/onlinebanking.jar query email madi
      - Find customers by email using fuzzy search.

  java -jar target/onlinebanking.jar query spending 1 groceries
      - Query customer spending by customer id and category using full-text search.

  java -jar target/onlinebanking.jar campaign campaign1 5000
      - Use Federated queries (BigQuery) to find customers that match a marketing campaign by name based on a recent spending threshold.

  java -jar target/onlinebanking.jar delete
      - Delete sample Cloud Spanner database.
```

10. Run through the available commands, starting with `create` and `insert`, then try the advanced queries and other features

The different `stepN` folders are related to the codelab for this sample.  They show the complete code after each logical step in the codelab.

## Cleanup

1. Delete the Spanner instance:

```bash
gcloud spanner instances delete cloudspanner-onlinebanking
```

2. Delete the BigQuery connection and dataset:

```bash
bq rm --connection --location=us-central1 spanner-connection
bq rm -r MarketingCampaigns
```

## Test

ℹ️ Note that the tests require a Spanner instance to have already been created.  And the tests will create a new database with random characters to avoid deleting the database if already in use.

1. Configure the environment variables for the test:

```bash
export SPANNER_TEST_INSTANCE=cloudspanner-onlinebanking
export SPANNER_TEST_DATABASE=onlinebanking
```

2. Run the tests:

```bash
mvn integration-test
```
