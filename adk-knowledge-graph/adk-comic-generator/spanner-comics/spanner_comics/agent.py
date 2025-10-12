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
import datetime
import uuid
from zoneinfo import ZoneInfo
from dotenv import load_dotenv
from google.adk.agents import LlmAgent, SequentialAgent
from google.adk.agents.callback_context import CallbackContext

# Import the tools
from .loop_agent import image_scoring
from .tools.spanner_tool import query_knowledge_graph
from .tools.image_tool import generate_image
from .tools.storage_tool import save_to_gcs
from .tools.bigquery_tool import save_to_bigquery

load_dotenv()

# os.environ.setdefault("GOOGLE_GENAI_USE_VERTEXAI", "True")

# Check for required environment variables
required_env_vars = [
    "GOOGLE_CLOUD_PROJECT",
    "GCS_BUCKET_NAME",
    "BIGQUERY_DATASET_ID",
    "BIGQUERY_TABLE_ID",
]

missing_env_vars = [var for var in required_env_vars if not os.environ.get(var)]

if missing_env_vars:
    print(f"Error: The following environment variables are not set: {', '.join(missing_env_vars)}")
    print("Please set them before running the script.")
    exit()

# Wrapper functions for tools that need environment variables
def save_file_to_gcs(file_path: str, destination_blob_name: str) -> str:
    """Saves a file to a Google Cloud Storage bucket."""
    print(f"Current working directory: {os.getcwd()}")
    print(f"Saving file: {file_path}")
    return save_to_gcs(file_path, os.environ.get("GCS_BUCKET_NAME"), destination_blob_name)

def save_data_to_bigquery(rows_to_insert: list[dict]) -> None:
    """Saves data to a BigQuery table."""
    return save_to_bigquery(os.environ.get("BIGQUERY_DATASET_ID"), os.environ.get("BIGQUERY_TABLE_ID"), rows_to_insert)

def set_session(callback_context: CallbackContext):
    """
    Sets a unique ID and timestamp in the callback context's state.
    This function is called before the main_loop_agent executes.
    """

    callback_context.state["unique_id"] = str(uuid.uuid4())
    callback_context.state["timestamp"] = datetime.datetime.now(
        ZoneInfo("UTC")
    ).isoformat()

# Define the sub-agents
knowledge_graph_agent = LlmAgent(
    name="knowledge_graph_agent",
    model="gemini-2.5-pro",
    description="Queries the knowledge graph to get information about Spanner.",
    instruction="""You are a knowledge graph agent. You will be given a question and you will query the knowledge graph to get the answer. """,
    tools=[query_knowledge_graph],
)

save_agent = LlmAgent(
    name="save_agent",
    model="gemini-2.5-pro",
    description="Saves the generated image and other data.",
    instruction="You are a save agent. You will be given a file path and you will save it to Google Cloud Storage and BigQuery.",
    tools=[save_file_to_gcs, save_data_to_bigquery],
)


from .tools.conditional_save_tool import conditional_save


conditional_save_agent = LlmAgent(
    name="conditional_save_agent",
    model="gemini-2.5-pro",
    description="Conditionally saves the image to GCS and BigQuery if the quality check passes.",
    instruction="You are a conditional save agent. You will be given a JSON object with the status and file path, and you will save the image if the status is success.",
    tools=[conditional_save],
)

story_generation_agent = LlmAgent(
    name="story_generation_agent",
    model="gemini-2.5-pro",
    description="Creates a story based on the provided text.",
    instruction="You are a story generation agent. You will be given a text and you will create a story based on it. You will output a JSON object with a 'story' key.",
)

# Define the root agent
root_agent = SequentialAgent(
    name="spanner_comics_agent",
    description="An agent that can answer questions about Spanner based on its documentation and generate comics.",
    sub_agents=[knowledge_graph_agent, story_generation_agent, image_scoring, conditional_save_agent],
    before_agent_callback=set_session,
)

