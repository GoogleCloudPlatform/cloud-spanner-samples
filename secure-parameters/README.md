# Spanner Secure Parameters Examples

This directory contains examples of how to use Cloud Client libraries with the Secure Parameters feature (also known as Parameterized Secure Views).

Secure Parameters allow you to pass context information (like user ID or roles) securely from the client application to Spanner, which can then be used in views (with `SQL SECURITY DEFINER`) to restrict access to data.

These examples demonstrate:
1.  Creating a database.
2.  Defining a schema with a table, a secure view using `SECURE_CONTEXT` (GoogleSQL) or `spanner.secure_context` (PostgreSQL), and database roles.
3.  Inserting sample data.
4.  Querying the view using `ClientContext` with `SecureContext` set to different values.
5.  Cleaning up the database.

## Prerequisites

1.  **Google Cloud Project:** You need a GCP project with the Cloud Spanner API enabled.
2.  **Spanner Instance:** You need a Spanner instance.
3.  **Authentication:** Set up Application Default Credentials:
    ```bash
    gcloud auth application-default login
    ```
4.  **Environment Variables:** Set the following environment variables:
    ```bash
    export GOOGLE_CLOUD_PROJECT="your-gcp-project-id"
    export SPANNER_INSTANCE_ID="your-spanner-instance-id"
    ```

## Languages

Detailed instructions for each language:

*   [Java](./java/README.md)
*   [Go](./go/README.md)
*   [Python](./python/README.md)
*   [C# (.NET)](./dotnet/README.md)
