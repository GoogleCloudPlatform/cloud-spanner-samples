import csv
import json
import asyncio
import os
import vertexai
from vertexai.generative_models import GenerativeModel
from google.cloud import spanner
from google.adk.agents import Agent
from google.adk.runners import Runner
from google.adk.tools import FunctionTool
from google.adk.sessions import InMemorySessionService
from google.genai import types
from datetime import datetime

# --- INITIALIZATION ---
PROJECT_ID = "xxxx"
INSTANCE_ID = "graphxx"
DATABASE_ID = "marketinggraph"
APP_NAME = "ContextBuilder"
USER_ID = "user_123"
SESSION_ID = "session_456"

os.environ["GOOGLE_CLOUD_SPANNER_ENABLE_METRICS"] = "false"
os.environ["OTEL_SDK_DISABLED"] = "true"


os.environ["GOOGLE_CLOUD_PROJECT"] = PROJECT_ID
os.environ["GOOGLE_CLOUD_LOCATION"] = "us-central1"
os.environ["GOOGLE_GENAI_USE_VERTEXAI"] = "True"

vertexai.init(project=PROJECT_ID, location="us-central1")
model = GenerativeModel("gemini-2.5-flash") # Global model for tools

spanner_client = spanner.Client()
instance = spanner_client.instance(INSTANCE_ID)
database = instance.database(DATABASE_ID)

# --- TOOL 1: THE STRATEGIC EXTRACTOR ---
def extract_context(ae_notes: str) -> str:
    """Extracts strategic metadata (Type, Reasoning) from CRM notes."""
    prompt = f"""
    Extract strategic metadata from these CRM notes: 
    {ae_notes}
    Return JSON format with these keys: 
    - decision_type
    - reasoning
    - policy_hint
    """
    response = model.generate_content(
        prompt, 
        generation_config={"response_mime_type": "application/json"}
    )
    return response.text # Return as string, Agent will handle it

ContextExtractorTool = FunctionTool(func=extract_context)

# --- TOOL 2: THE SPANNER GRAPH INGESTOR ---
def ingest_graph_data(row_json: str, extracted_logic_json: str) -> str:
    """Uses standard SQL DML to update tables and build the Causal Chain."""
    row = json.loads(row_json)
    logic = json.loads(extracted_logic_json)
    # 1. Convert the string date (e.g., '2025-01-15') to a Python datetime object
    # Adjust the format '%Y-%m-%d' if your CSV uses a different layout
    try:
        ts_value = datetime.strptime(row['event_date'], '%Y-%m-%d')
    except ValueError:
        # Fallback if date format is slightly different or missing
        ts_value = datetime.utcnow()
    
    def _run_sql_ingestion(transaction):
        # 1. Upsert the Customer (The Anchor)
        transaction.execute_update(
            "INSERT OR UPDATE Customers (customer_id, tier, mrr, industry) "
            "VALUES (@cid, @tier, @mrr, @ind)",
            params={
                "cid": str(row['account_id']),
                "tier": str(row.get('tier', 'Unknown')),
                "mrr": float(row.get('revenue_impact', 0)),
                "ind": str(row.get('industry', 'Unknown'))
            }
        )

        # 2. Insert the Decision (The Action)
        # Unique ID prevents duplicates if the script is re-run for the same row
        unique_id = f"{row['account_id']}-{row['event_date']}"
        transaction.execute_update(
            "INSERT Decisions (decision_id, type, reasoning_text, timestamp) "
            "VALUES (@did, @type, @reason, @ts)",
            params={
                "did": f"DEC-{unique_id}",
                "type": str(logic.get('decision_type', 'General')),
                "reason": str(logic.get('reasoning', 'No reasoning provided')),
                "ts": ts_value
            }
        )

        # 3. Insert the Outcome (The Result)
        transaction.execute_update(
            "INSERT Outcomes (outcome_id, result, revenue_impact) "
            "VALUES (@oid, @res, @rev)",
            params={
                "oid": f"OUT-{unique_id}",
                "res": str(row.get('contract_status', 'Unknown')),
                "rev": float(row.get('revenue_impact', 0))
            }
        )

        # 4. Create the Edges (The Relationships)
        # AboutCustomer Edge
        transaction.execute_update(
            "INSERT AboutCustomer (decision_id, customer_id) VALUES (@did, @cid)",
            params={"did": f"DEC-{unique_id}", "cid": str(row['account_id'])}
        )

        # ResultedIn Edge
        transaction.execute_update(
            "INSERT ResultedIn (decision_id, outcome_id) VALUES (@did, @oid)",
            params={"did": f"DEC-{unique_id}", "oid": f"OUT-{unique_id}"}
        )

    database.run_in_transaction(_run_sql_ingestion)
    return f"Successfully codified SQL history for {row['account_id']}."

SpannerGraphIngestorTool = FunctionTool(func=ingest_graph_data)

# --- AGENT DEFINITION ---
context_agent = Agent(
    name="Institutional_Memory_Builder",
    model="gemini-2.5-flash",
    instruction="""
    You are an expert at turning unstructured CRM logs into structured Graph data.
    Your goal is to build a Causal Chain (Decision -> Outcome) for every customer provided in the CSV.
    
    For every row in the provided data:
    1. Pass the 'ae_notes' to extract_context.
    2. Pass the entire row and the extracted logic to ingest_graph_data.
    
    Work through the data systematically. Confirm when all records are processed.
    """,
    tools=[extract_context, ingest_graph_data]
)

# --- ASYNC RUNNER ---
async def main():
    csv_path = 'crm_history.csv'
    try:
        with open(csv_path, mode='r') as file:
            raw_csv_data = file.read()
    except FileNotFoundError:
        print(f"Error: {csv_path} not found.")
        return

    session_service = InMemorySessionService()
    await session_service.create_session(app_name=APP_NAME, user_id=USER_ID, session_id=SESSION_ID)

    runner = Runner(agent=context_agent, app_name=APP_NAME, session_service=session_service)

    # We pass the raw CSV data as the context for the agent
    query = f"Please process this CRM history data into the Spanner Graph:\n\n{raw_csv_data}"
    content = types.Content(role='user', parts=[types.Part(text=query)])

    print("--- Starting Agentic Ingestion ---")
    events = runner.run(user_id=USER_ID, session_id=SESSION_ID, new_message=content)

    for event in events:
        # ADK allows you to see the "Thought Trace" or Tool Calls if you iterate through events
        if hasattr(event, 'content') and event.content.parts:
            print(f"Agent: {event.content.parts[0].text}")

if __name__ == "__main__":
    asyncio.run(main())
