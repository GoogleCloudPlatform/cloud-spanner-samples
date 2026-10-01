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
import google.auth

from google.auth.transport.requests import Request
from google.adk.agents import Agent
from google.adk.tools.spanner import client as spanner_client_module
from google.adk.tools.spanner.settings import SpannerToolSettings, Capabilities
from google.adk.tools.spanner.spanner_credentials import SpannerCredentialsConfig
from google.adk.tools.spanner.spanner_toolset import SpannerToolset

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

# --- Auth ---
try:
    application_default_credentials, _ = google.auth.default()
    if not application_default_credentials.valid:
        application_default_credentials.refresh(Request())
except Exception as e:
    print(f"\n[ERROR] Failed to authenticate: {e}", flush=True)
    sys.exit(1)

# --- Spanner Tool config ---
credentials_config = SpannerCredentialsConfig(credentials=application_default_credentials)
tool_settings = SpannerToolSettings(capabilities=[Capabilities.DATA_READ])
spanner_toolset = SpannerToolset(credentials_config=credentials_config, spanner_tool_settings=tool_settings)

root_agent = Agent(
    model="gemini-3.8-flash",
    name="spanner_healthcare_agent",
    description="Agent to answer questions about Spanner database and execute SQL queries.",
    instruction=f"""
        You are a data assistant agent with access to several Spanner tools.
        Make use of those tools to answer the user's questions.

        When using your tools, always use the following default database configuration:
        - project_id: {PROJECT_ID}
        - instance_id: {INSTANCE_ID}
        - database_id: {DATABASE_ID}
    """,
    tools=[
        spanner_toolset,
    ],
)