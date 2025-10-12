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

# This tools is currently not being used, it can be added

import os
from googleapiclient.discovery import build

def google_search(query: str) -> str:
    """Performs a Google search and returns the results."""
    api_key = os.environ.get("GOOGLE_API_KEY")
    cse_id = os.environ.get("CUSTOM_SEARCH_ENGINE_ID")

    service = build("customsearch", "v1", developerKey=api_key)
    res = service.cse().list(q=query, cx=cse_id, num=5).execute()

    if 'items' in res:
        return "\n".join([item['link'] for item in res['items']])
    else:
        return "No results found."

if __name__ == '__main__':
    # Example usage
    query = "Spanner documentation"
    search_results = google_search(query)
    print(f"Search results: {search_results}")