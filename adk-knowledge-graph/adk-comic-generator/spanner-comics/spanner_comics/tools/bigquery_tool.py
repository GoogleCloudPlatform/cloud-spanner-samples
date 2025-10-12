# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This tool is currently not being called, it's a placeholder
# to replace the GCS bucket store or further enhance it

from google.cloud import bigquery

def save_to_bigquery(dataset_id: str, table_id: str, rows_to_insert: list) -> None:
    """Saves data to a BigQuery table."""
    client = bigquery.Client(location='us')
    table_ref = client.dataset(dataset_id).table(table_id)
    table = client.get_table(table_ref)

    errors = client.insert_rows_json(table, rows_to_insert)
    if errors == []:
        print("New rows have been added.")
    else:
        print("Encountered errors while inserting rows: {}".format(errors))

if __name__ == '__main__':
    # Example usage
    dataset_id = "your_dataset_id"
    table_id = "your_table_id"
    rows_to_insert = [
        {"image_ref": "gs://bucket/image.jpg", "doc_ref": "https://cloud.google.com/spanner/docs"}
    ]
    save_to_bigquery(dataset_id, table_id, rows_to_insert)