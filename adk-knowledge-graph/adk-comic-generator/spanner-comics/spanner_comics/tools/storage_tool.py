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

from google.cloud import storage

def save_to_gcs(file_path: str, bucket_name: str, destination_blob_name: str) -> str:
    """Saves a file to a Google Cloud Storage bucket."""
    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(destination_blob_name)

    blob.upload_from_filename(file_path)

    return f"gs://{bucket_name}/{destination_blob_name}"

if __name__ == '__main__':
    # Example usage
    # Create a dummy file to upload
    with open("dummy_file.txt", "w") as f:
        f.write("This is a dummy file.")
        
    file_path = "dummy_file.txt"
    bucket_name = "your-gcs-bucket-name"
    destination_blob_name = "spanner-comics/dummy_file.txt"
    gcs_path = save_to_gcs(file_path, bucket_name, destination_blob_name)
    print(f"File saved to GCS: {gcs_path}")