# Spanner Knowledge Graph Builder

This Python demo application scrapes online documentation and YouTube videos and loads it into a Spanner database as a knowledge graph using LangChain.

## Prerequisites

*   Python 3.9+
*   [uv](https://github.com/astral-sh/uv) - An extremely fast Python package installer and resolver.
*   A Google Cloud Project.

## Setup

### 1. Google Cloud Authentication

First, you need to authenticate with Google Cloud. If you have the Google Cloud CLI installed, you can run the following command:

```bash
gcloud auth application-default login
```

### 2. Spanner Setup

You need a Spanner instance [with Graph capabilities](https://cloud.google.com/spanner/docs/instances?utm_campaign=CDR_0x6cb6c9c7_default_b450953078&utm_medium=external&utm_source=social) and a database.

**Create a Spanner instance:**

```bash
gcloud spanner instances create <your-instance-id> --config=regional-us-central1 --description="Spanner KB instance" --nodes=1
```

Set the instance ID in the `.env` file.

**Create a Spanner database:**

```bash
gcloud spanner databases create <your-database-id> --instance=<your-instance-id>
```

Set the database ID in the `.env` file.

This loader will create the necessary tables and graph.

### 3. Set Environment Variables

This application requires the following environment variables to be set to connect to your Spanner database:

- `SPANNER_PROJECT`: Your Google Cloud project ID.
- `SPANNER_INSTANCE`: Your Spanner instance ID.
- `SPANNER_DATABASE`: Your Spanner database ID.

Make a copy of `.env.example` into a `.env` file and set your variables there.

## Installation

### 1. Create a Virtual Environment using `uv`

It is recommended to use a virtual environment to manage dependencies.

```bash
uv venv
```

This will create a `.venv` directory in your project folder.

### 2. Activate the Virtual Environment

```bash
source .venv/bin/activate
```

### 3. Install Dependencies using `uv`

Install the necessary Python packages from the `requirements.txt` file.

```bash
uv pip install -r requirements.txt
```

## Usage

Once the setup is complete, you can run the application with the following command:

```bash
python main.py
```

The script will then:
1. Scrape the documentation from the predefined list of URLs.
2. Create LangChain `Document` objects.
3. Save these documents to the specified Spanner table.


## Deploying as a Cloud Run Job

You can also run this application as a Cloud Run job, which is useful for long-running processes.

### 1. Enable APIs

Enable the Artifact Registry and Cloud Run APIs:
```bash
gcloud services enable artifactregistry.googleapis.com run.googleapis.com
```

### 2. Create an Artifact Registry Repository

Create a repository to store your container images:
```bash
gcloud artifacts repositories create <your-repo-name> --repository-format=docker --location=us-central1
```

### 3. Build the Container Image

Build the container image using Cloud Build and push it to Artifact Registry.

```bash
gcloud builds submit --tag us-central1-docker.pkg.dev/<your-project-id>/<your-repo-name>/spanner-kb-generator
```

### 4. Create or Update the Cloud Run Job

Create or update the Cloud Run job. Since this job can take a while, we'll set the timeout to 1 hour (3600 seconds).

```bash
gcloud run jobs update spanner-kb-generator-job --image us-central1-docker.pkg.dev/<your-project-id>/<your-repo-name>/spanner-kb-generator --timeout=3600 --region us-central1
```

### 5. Execute the Job

Execute the Cloud Run job.

```bash
gcloud run jobs execute spanner-kb-generator-job --region us-central1 --wait
```
