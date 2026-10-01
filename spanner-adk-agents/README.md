# Build SQL and Semantic Search Agents with the ADK and Spanner

This directory contains a sample demonstrating how to build a healthcare agent using the [Agent Development Kit (ADK)](https://adk.dev/) integration with **Cloud Spanner**. It is based on [this codelab](https://codelabs.developers.google.com/spanner-adk) (coming soon!).

The agents in this sample show you how to:

* Create a Spanner database and add an embedding column
* Configure ADK's SpannerToolset and SpannerToolSettings.
* Enable database metadata inspection, SQL execution, and vector similarity search tools.

While this example shows a healthcare use case, the capabilities demonstrated in this sample can be applied to many industries.

## Prerequisites

1. A Google Cloud project with billing enabled.
2. The following APIs must be enabled:
   ```bash
   gcloud services enable spanner.googleapis.com aiplatform.googleapis.com
   ```

## Environment variables

To run the samples, set the following env vars:

```shell
# Your Google Cloud Project ID
export GOOGLE_CLOUD_PROJECT=$(gcloud config get-value project)

# Google Cloud location
export GOOGLE_CLOUD_LOCATION="us"

# Instruct ADK to use Vertex AI rather than the public Gemini API
export GOOGLE_GENAI_USE_VERTEXAI=True

# Your Spanner instance and database configuration
export SPANNER_INSTANCE_ID="healthcare"
export SPANNER_DATABASE_ID="medical-db"
```

## Create Spanner instance and database

Run the following commands using the `gcloud` CLI to create a Spanner instance and DB to use for this sample:

### Create the Spanner instance
gcloud spanner instances create $SPANNER_INSTANCE_ID \
    --config=regional-us-central1 \
    --description="ADK Sample Instance" \
    --edition=enterprise \
    --processing-units=1000

### Create the database
gcloud spanner databases create $SPANNER_DATABASE_ID \
    --instance=$SPANNER_INSTANCE_ID


## Setup

1. Install Dependencies:
Clone the repository, navigate to this directory, and install the required Python packages:

```shell
pip install -r requirements.txt
```

2. Create tables in your database and load data:

First, execute the queries provided in `create_tables.sql` to create the `Providers`, `Patients`, `Appointments`, and `Prescriptions` tables and populate them with sample data.

Next, run the queries in `embeddings.sql` to generate the vector embeddings and populate them into the `DoctorNotesEmbedding` column.

**Important**: When running the `CREATE OR REPLACE MODEL TextEmbeddingModel` statement, ensure you replace `YOUR_PROJECT_ID` with your actual Google Cloud Project ID.

## Running the Agents Locally

This sample contains three different agents:

* `basic_spanner_agent`: Answers metadata questions and executes SQL to summarize your database.
* `semantic_agent`: Performs vector similarity search on unstructured doctor notes.
* `secure_agent`: Demonstrates how to restrict agent access to only an allowed list of tables.

To test any of the agents locally with the ADK Web UI, run the following command from the root of this sample directory:

```shell
adk web --allow_origins="regex:.*" --session_service_uri="memory://" .
```

Navigate to http://127.0.0.1:8000 in your browser. Use the dropdown at the top of the interface to switch between the different agents and interact with them.

## Cleanup

To avoid incurring unexpected charges to your Google Cloud billing account, make sure to delete the Spanner instance when you are done testing this sample. 

Deleting the instance will also automatically delete the `medical-db` database and all of its data.

```bash
gcloud spanner instances delete $SPANNER_INSTANCE_ID --quiet
