# Spanner Comics Agent

This agent uses a knowledge graph in Spanner to answer questions and generate comics based on the answers.


## Pre-requisites

1. Create a Google Cloud Project and enable the [Vertex AI API](https://console.cloud.google.com/vertex-ai).

2. A Spanner instance and database with Graph (either [a trial instance](https://cloud.google.com/spanner/docs/free-trial-instance?utm_campaign=CDR_0x6cb6c9c7_default_b450953078&utm_medium=external&utm_source=social) or an [Enterprise one](https://cloud.google.com/spanner/docs/editions-overview?utm_campaign=CDR_0x6cb6c9c7_default_b450953078&utm_medium=external&utm_source=social))

3. Get a `GOOGLE_API_KEY` from [Google AI Studio](https://aistudio.google.com/). This is the simplest way to get started.

4. If you are not using the [Google Cloud Shell](https://cloud.google.com/shell/docs?utm_campaign=CDR_0x6cb6c9c7_default_b450953078&utm_medium=external&utm_source=social), install the [Google Cloud CLI](https://cloud.google.com/sdk/docs/install).

Authenticate your local environment by running:
            ```
            gcloud auth login
            ```
5.  You will need a knowledge graph in Spanner with embeddings. You can generate one with the sample called [knowledge-graph-loader](https://github.com/GoogleCloudPlatform/cloud-spanner-samples/tree/main/knowledge-graph-loader).

## Setup

1.  Install `uv` (Recommended): Python package manager
    ```
    pip install uv
    ```


2.  Set up your environment variables by copying the `.env.example` to `.env` and filling in the values.
    ```bash
    cp .env.example .env
    ```
3.  Export the API key as an environment variable to run locally:
    ```
    export GOOGLE_API_KEY=<<the API key from Google AI Studio>>
    ```


## Running the Agent

To run the agent, navigate to the `spanner-comics` directory and run the following command:

```bash
uv venv .venv --python 3.11 && source .venv/bin/activate && uv pip install -r requirements.txt && adk web
```




