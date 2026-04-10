import csv
import json
import time
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
INSTANCE_ID = "graph"
DATABASE_ID = "benchmarkgraphdb"
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

# --- GLOBAL METRICS LOG ---
# Changed to a dictionary to "group" by account_id
metrics_data = {}

def log_step_metric(account_id, step, latency, tokens=0):
    if account_id not in metrics_data:
        metrics_data[account_id] = {"account_id": account_id}
    
    if step == "Extraction":
        metrics_data[account_id]["inference_sec"] = latency
        metrics_data[account_id]["tokens"] = tokens
    elif step == "Ingestion":
        metrics_data[account_id]["ingestion_sec"] = latency

# --- TOOL 1: THE STRATEGIC EXTRACTOR ---
def extract_context(ae_notes: str, account_id: str) -> str:
    """Extracts strategic metadata (Signal, Decision, Reasoning) from CRM notes."""
    prompt = f"""
    Analyze the following CRM notes and extract the strategic intent.
    
    CRM NOTES:
    {ae_notes}
    
    Return a JSON object with exactly these keys:
    - signal_type: Categorize the primary customer risk/trigger (e.g., 'LOW_ADOPTION', 'COMPETITOR_THREAT', 'BUDGET_CONSTRAINTS', 'EXPANSION_OPPORTUNITY').
    - decision_type: The specific action taken (e.g., 'Discount', 'Strategic Advisory Workshop', 'Tier Migration').
    - reasoning: A brief (15-20 word) summary of why this decision was made.
    """
    start_time = time.time()
    response = model.generate_content(
        prompt, 
        generation_config={"response_mime_type": "application/json"}
    )
    latency = time.time() - start_time
    tokens = response.usage_metadata.total_token_count
    log_step_metric(account_id, "Extraction", round(latency,2), tokens)
    return response.text

ContextExtractorTool = FunctionTool(func=extract_context)

# --- TOOL 2: THE SPANNER GRAPH INGESTOR ---
def ingest_graph_data(row_json: str, extracted_logic_json: str) -> str:
    """Idempotent upsert logic following a strictly decoupled Graph Schema."""
    row = json.loads(row_json)
    logic = json.loads(extracted_logic_json)
    
    try:
        ts_value = datetime.strptime(row['event_date'], '%Y-%m-%d')
    except (ValueError, KeyError):
        ts_value = datetime.utcnow()
    
    # Deterministic keys for Idempotency
    #Assuming only one decision and outcome can be taken per day for a customer for the primary key logic
    unique_key = f"{row['account_id']}_{row['event_date']}".replace("-", "")
    dec_id = f"DEC_{unique_key}"
    out_id = f"OUT_{unique_key}"

    def _run_sql_upsert(transaction):
        # 1. Upsert Customer (Node)
        transaction.execute_update(
            "INSERT OR UPDATE Customers (customer_id, tier, industry) "
            "VALUES (@cid, @tier, @ind)",
            params={
                "cid": str(row['account_id']),
                "tier": str(row.get('tier', 'Unknown')),
                "ind": str(row.get('industry', 'Unknown'))
            }
        )

        # 2. Upsert Decision (Node)
        transaction.execute_update(
            "INSERT OR UPDATE Decisions (decision_id, customer_id, signal_type, decision_type, reasoning_text, timestamp) "
            "VALUES (@did, @cid, @signal, @d_type, @reason, @ts)",
            params={
                "did": dec_id,
                "cid": str(row['account_id']),
                "signal": str(logic.get('signal_type', 'UNKNOWN')),
                "d_type": str(logic.get('decision_type', 'General')),
                "reason": str(logic.get('reasoning', 'No reasoning provided')),
                "ts": ts_value
            }
        )

        # 3. Upsert Outcome (Node) - decision_id REMOVED per schema design
        transaction.execute_update(
            "INSERT OR UPDATE Outcomes (outcome_id, result, revenue_impact) "
            "VALUES (@oid, @res, @rev)",
            params={
                "oid": out_id,
                "res": str(row.get('contract_status', 'Unknown')),
                "rev": float(row.get('revenue_impact', 0))
            }
        )

        # 4. Upsert Edges (Relationships)
        # These tables manage the connections in the Graph
        transaction.execute_update(
            "INSERT OR IGNORE AboutCustomer (decision_id, customer_id) VALUES (@did, @cid)",
            params={"did": dec_id, "cid": str(row['account_id'])}
        )

        transaction.execute_update(
            "INSERT OR IGNORE ResultedIn (decision_id, outcome_id) VALUES (@did, @oid)",
            params={"did": dec_id, "oid": out_id}
        )

    start_time = time.time()
    database.run_in_transaction(_run_sql_upsert)
    latency = time.time() - start_time
    log_step_metric(str(row['account_id']), "Ingestion", latency)
    return f"Codified Causal Chain: {dec_id} -> {out_id}"

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
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
    csv_path = os.path.join(SCRIPT_DIR, "crm_history_benchmark.csv")
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

    # At the end of main()
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
    metrics_csv_path = os.path.join(SCRIPT_DIR, "final_benchmark_report.csv")
    final_report = list(metrics_data.values())
    with open(metrics_csv_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["account_id", "inference_sec", "ingestion_sec", "tokens"])
        writer.writeheader()
        writer.writerows(final_report)

if __name__ == "__main__":
    asyncio.run(main())
