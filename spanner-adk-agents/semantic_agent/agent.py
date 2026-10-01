# Copyright 2026 Google LLC
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
import sys
import asyncio
import google.auth

from google.auth.transport.requests import Request
from google.adk.agents import Agent
from google.adk.tools.spanner import utils as spanner_utils
from google.adk.tools.spanner import client as spanner_client_module
from google.adk.tools.spanner.settings import SpannerToolSettings, Capabilities, SpannerVectorStoreSettings
from google.adk.tools.spanner.spanner_credentials import SpannerCredentialsConfig
from google.adk.tools.spanner.spanner_toolset import SpannerToolset
from google.genai import Client

# --- Environment Variable Validation ---
REQUIRED_ENV_VARS = [
    "GOOGLE_CLOUD_PROJECT",
    "GOOGLE_CLOUD_LOCATION",
    "GOOGLE_GENAI_USE_VERTEXAI",
    "SPANNER_INSTANCE_ID",
    "SPANNER_DATABASE_ID"
]

missing_vars = [var for var in REQUIRED_ENV_VARS if not os.environ.get(var)]
if missing_vars:
    print(f"\n[ERROR] Missing required environment variables: {', '.join(missing_vars)}", flush=True)
    print("Please set them before running the agent. See the README.md for instructions.\n", flush=True)
    sys.exit(1)

PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT")
INSTANCE_ID = os.environ.get("SPANNER_INSTANCE_ID")
DATABASE_ID = os.environ.get("SPANNER_DATABASE_ID")

# --- Cloud Shell Workaround ---
# If you are running this in Cloud Shell, you need to apply a small patch to prevent
# the local proxy from deadlocking Spanner's gRPC cleanup.
# You do not need this in standard production environments like Cloud Run or your local laptop.
if hasattr(spanner_client_module, "_close_spanner_resources"):
    spanner_client_module._close_spanner_resources = lambda *args, **kwargs: None

# --- Auth and credentials config ---
try:
    application_default_credentials, _ = google.auth.default()
    if not application_default_credentials.valid:
        application_default_credentials.refresh(Request())
except Exception as e:
    print(f"\n[ERROR] Failed to authenticate: {e}", flush=True)
    sys.exit(1)

credentials_config = SpannerCredentialsConfig(
    credentials=application_default_credentials
)

# Because text-embedding-005 uses a regional endpoint (i.e. 'us-central1')
# and gemini-3.8-flash uses a multi-regional endpoint (i.e. 'us')
# We need the following patch to pass the correct region to the embedding model
regional_client = Client(vertexai=True, project=PROJECT_ID, location="us-central1")
async def _patched_embed_contents_async(
        vertex_ai_embedding_model_name: str,
        contents: list[str],
        output_dimensionality: int | None = None,
        genai_client: Client | None = None
    ):
        return await asyncio.to_thread(
            spanner_utils.embed_contents, vertex_ai_embedding_model_name, contents,
            output_dimensionality=output_dimensionality, genai_client=regional_client,
        )
spanner_utils.embed_contents_async = _patched_embed_contents_async

my_vector_store_settings = SpannerVectorStoreSettings(
    project_id=PROJECT_ID,
    instance_id=INSTANCE_ID,
    database_id=DATABASE_ID,
    table_name="Appointments",
    content_column="DoctorNotes",
    embedding_column="DoctorNotesEmbedding",
    vector_length=768,
    vertex_ai_embedding_model_name="text-embedding-005",
    selected_columns=["PatientId", "ProviderId", "AppointmentDate", "DoctorNotes"],
    nearest_neighbors_algorithm="EXACT_NEAREST_NEIGHBORS",
    top_k=3,
    distance_type="COSINE",
)

my_tool_settings = SpannerToolSettings(
    capabilities=[Capabilities.DATA_READ],
    vector_store_settings=my_vector_store_settings,
)

my_spanner_toolset = SpannerToolset(
    credentials_config=credentials_config,
    spanner_tool_settings=my_tool_settings,
    tool_filter=["vector_store_similarity_search"],
)

root_agent = Agent(
    model="gemini-3.8-flash",
    name="doctor_notes_agent",
    description="Semantic search agent for medical notes.",
    instruction="""
    You are a medical assistant that returns patient and provider IDs based on the query.
    1. Always use the `spanner_vector_store_similarity_search` tool to find associated IDs.
    2. If no relevant information is found, state that no patient notes matched the request.
    3. Present the patient ID, provider ID, and relevant doctor notes to make it clear why each patient and provider were returned for a particular search.
    """,
    tools=[my_spanner_toolset],
)