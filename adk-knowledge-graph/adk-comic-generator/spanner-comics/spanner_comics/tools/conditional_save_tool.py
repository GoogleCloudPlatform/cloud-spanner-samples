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


import os
import json
from .storage_tool import save_to_gcs
from .bigquery_tool import save_to_bigquery

def conditional_save(data: str) -> str:
    """Conditionally saves the image to GCS and BigQuery if the quality check passes."""
    try:
        data = json.loads(data)
        if data.get("status") == "success":
            file_path = data.get("file_path")
            if file_path:
                # Save to GCS
                gcs_path = save_to_gcs(file_path, os.environ.get("GCS_BUCKET_NAME"), os.path.basename(file_path))
                
                # # Save to BigQuery
                # rows_to_insert = [
                #     {
                #         "file_path": file_path,
                #         "gcs_path": gcs_path,
                #         "status": "success",
                #     }
                # ]
                # save_to_bigquery(os.environ.get("BIGQUERY_DATASET_ID"), os.environ.get("BIGQUERY_TABLE_ID"), rows_to_insert)
                
                return f"Successfully saved {file_path} to GCS."
            else:
                return "Error: file_path not found in the input data."
        else:
            return "Image quality check failed. Skipping save."
    except Exception as e:
        return f"Error in conditional_save: {e}"
