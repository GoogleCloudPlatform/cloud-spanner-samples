# Spanner Conversational Analytics Examples

This directory contains examples demonstrating how to use the Conversational Analytics API with Google Cloud Spanner. These examples show how to build data agents that can understand natural language questions and generate SQL queries to retrieve answers from your Spanner database.

**Prerequisites:**

*   A Google Cloud Project with Spanner and an active Spanner instance.
*   The Cloud AI Companion API enabled.
*   Appropriate IAM permissions to access Spanner and the Conversational Analytics API. See [Conversational Analytics API access control with IAM](https://docs.cloud.google.com/gemini/data-agents/conversational-analytics-api/access-control) for details.
*   `gcloud` CLI installed and configured.
*   Python 3.7+ (for SDK examples).

## Examples

This guide provides examples for interacting with the API using both the Python Client Library (SDK) and HTTP requests (curl).

### 1. Using the Python SDK

This example shows how to set up a Data Agent and have a conversation to query your Spanner database.

**Installation:**

```bash
pip install google-cloud-aiplatform
