# Spanner Columnar Engine Benchmark

This repository provides the schema and queries to benchmark the performance of [Spanner Columnar Engine](https://cloud.google.com/spanner/docs/columnar-engine). By running these analytical queries, you can compare query latency and efficiency with and without the Columnar Engine enabled, using a public dataset.

The Columnar Engine is designed to accelerate analytical query performance (OLAP) on your data in Spanner, often by orders of magnitude. This is achieved by a combination of columnar storage and vectorized execution.

In the instructions below, we will:

1.  Create a Spanner database with the target schema and load benchmark dataset.
2.  Convert the data fully into columnar format.
3.  Run the provided analytical queries and observe the benefits of the Columnar Engine.

## Before you begin

1.  **Enable the Spanner API:** Make sure the Cloud Spanner API is enabled in your Google Cloud project.
    *   [Enable Spanner API](https://console.cloud.google.com/apis/library/spanner.googleapis.com)

2.  **Create a Spanner Instance:** Provision a new Spanner instance or use an existing one. The Columnar Engine requires the **Enterprise** or **Enterprise Plus**  edition.
    *   [Create a Spanner instance](https://cloud.google.com/spanner/docs/create-manage-instances#create-instance)
    *   [Spanner Editions Overview](https://cloud.google.com/spanner/docs/editions-overview)

3.  **GCS Bucket:** Have a Google Cloud Storage bucket available to temporarily store the dataset and schema files.
    *   [Create a GCS Bucket](https://cloud.google.com/storage/docs/creating-buckets)

## Setup Instructions

1.  **Download Dataset:** Download the CSV version of [this dataset](https://github.com/ClickHouse/ClickBench?tab=readme-ov-file#data-loading).

2.  **Download Schema:** Download the Spanner table schema definition: [benchmark-schema.sql](https://github.com/GoogleCloudPlatform/cloud-spanner-samples/blob/main/columnar-engine/benchmark-schema.sql)

3.  **Upload to GCS:**
    *   Extract the downloaded `hits.csv` file.
    *   Upload the `hits.csv` file to your GCS bucket.
    *   Upload the `benchmark-schema.sql` file to your GCS bucket.

4.  **Create the database and import Data:** Use the Spanner "Import" feature to load the `hits.csv` data from your GCS bucket into the `hits` table.
    *   In the Google Cloud Console, navigate to the Overview page of your Spanner instance.
    *   Click `Import my own data` button.
    *   Follow the wizard:
        *   Select `CSV file` as the file type,
        *   Select `hits.csv` file in your GCS bucket as the file location.
        *   Select `benchmark-schema.sql` file in your GCS bucket as the schema.
        *   Select `New database` as the destination, and choose a name for the new database.
        *   Click import.
    ![Import my own data](import_data.png)

5.  **Trigger Compaction:** After the data importing job is finished, [trigger a major compaction](https://cloud.google.com/spanner/docs/manual-data-compaction#trigger-compaction) to ensure the data is fully converted to the columnar format. Wait for the major compaction to finish.

## Run the Benchmark Queries

Run columnar and non-columnar queries in [benchmark-schema.sql](https://github.com/GoogleCloudPlatform/cloud-spanner-samples/blob/main/columnar-engine/benchmark-queries.sql) and compare the performance. 

Notes that `@{scan_method=columnar}` force Spanner to use Columnar Engine if available, while `@{scan_method=no_columnar}` explicitly opt-out Columnar Engine for the query. 
