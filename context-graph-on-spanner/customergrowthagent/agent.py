<<<<<<< HEAD
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

=======
>>>>>>> upstream/main
import asyncio
import uuid
import warnings
import os
import json
from google.adk.agents import Agent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.adk.tools import FunctionTool
from google.genai import types
from google.cloud import spanner
<<<<<<< HEAD
from dotenv import load_dotenv

# --- INITIALIZATION ---
load_dotenv()

PROJECT_ID = os.getenv("PROJECT_ID")
INSTANCE_ID = os.getenv("INSTANCE_ID")
DATABASE_ID = os.getenv("DATABASE_ID")
LOCATION = os.getenv("LOCATION", "us-central1")

GOOGLE_CLOUD_SPANNER_ENABLE_METRICS=os.getenv("GOOGLE_CLOUD_SPANNER_ENABLE_METRICS")
OTEL_SDK_DISABLED=os.getenv("OTEL_SDK_DISABLED")
GOOGLE_GENAI_USE_VERTEXAI=os.getenv("GOOGLE_GENAI_USE_VERTEXAI")

APP_NAME = os.getenv("GROWTH_APP_NAME")
USER_ID = os.getenv("USER_ID")
SESSION_ID = os.getenv("SESSION_ID")

os.environ["GOOGLE_CLOUD_PROJECT"] = PROJECT_ID
os.environ["GOOGLE_CLOUD_LOCATION"] = LOCATION

os.environ["GOOGLE_GENAI_USE_VERTEXAI"] = GOOGLE_GENAI_USE_VERTEXAI
os.environ["GOOGLE_CLOUD_SPANNER_ENABLE_METRICS"] = GOOGLE_CLOUD_SPANNER_ENABLE_METRICS
os.environ["OTEL_SDK_DISABLED"] = OTEL_SDK_DISABLED

warnings.filterwarnings("ignore", category=UserWarning)
=======

# --- 1. Environment & Telemetry ---
warnings.filterwarnings("ignore", category=UserWarning)
os.environ["GOOGLE_CLOUD_SPANNER_ENABLE_METRICS"] = "false"
os.environ["OTEL_SDK_DISABLED"] = "true"

PROJECT_ID = "xxx" #update project_id
INSTANCE_ID = "graphxx"
DATABASE_ID = "marketinggraph" # Matches your ingestion DB

os.environ["GOOGLE_CLOUD_PROJECT"] = PROJECT_ID
os.environ["GOOGLE_CLOUD_LOCATION"] = "us-central1"
os.environ["GOOGLE_GENAI_USE_VERTEXAI"] = "True"
>>>>>>> upstream/main

spanner_client = spanner.Client(project=PROJECT_ID)
instance = spanner_client.instance(INSTANCE_ID)
database = instance.database(DATABASE_ID)

# --- 2. Aligned Tool Functions ---

def get_customer_info(customer_id: str):
    """Retrieves industry, tier, and the LATEST risk signal from the Decisions table."""
    sql = """SELECT 
            c.industry, 
            c.tier, 
            d.signal_type 
        FROM Customers AS c
        JOIN Decisions AS d ON c.customer_id = d.customer_id
        WHERE c.customer_id = @cid
        ORDER BY d.timestamp DESC
        LIMIT 1"""
    with database.snapshot() as snapshot:
        results = snapshot.execute_sql(sql, params={'cid': customer_id}, 
                                       param_types={'cid': spanner.param_types.STRING})
        rows = list(results)
        if rows:
<<<<<<< HEAD
            return {"industry": rows[0][0], "tier": rows[0][1], "current_signal": rows[0][2]}
=======
            return {"industry": rows[0][0], "tier": rows[0][1]}
>>>>>>> upstream/main
        return "Customer not found."


def check_retention_history(industry: str, tier: str, signal_type: str):
    """Lookup successful patterns for a specific industry segment and behavioral signal."""
    # Uses f-string for industry/tier/signal to find 'Behavioral Twins'
    gql_query = f"""
    GRAPH MarketingContextGraph
    MATCH (c:Customers {{industry: '{industry}', tier: '{tier}'}})<-[:AboutCustomer]-(d:Decisions {{signal_type: '{signal_type}'}})-[:ResultedIn]->(o:Outcomes)
    WHERE o.result = 'Renewed'
    RETURN 
      d.timestamp AS Date,
      d.decision_type AS Action_Type,
      d.reasoning_text AS Success_Logic
    ORDER BY d.timestamp DESC
    LIMIT 3
    """
    with database.snapshot() as snapshot:
        results = snapshot.execute_sql(gql_query)
        rows = list(results)
        return [{"date": str(r[0]), "type": r[1], "logic": r[2]} for r in rows]

def get_policy_details(policy_id: str):
    """Retrieves corporate rules (e.g., POL-444 Margin Protection)."""
    sql = "SELECT name, rule_definition FROM Policies WHERE policy_id = @pid"
    with database.snapshot() as snapshot:
        results = snapshot.execute_sql(sql, params={'pid': policy_id}, 
                                       param_types={'pid': spanner.param_types.STRING})
        rows = list(results)
        return {"name": rows[0][0], "rule": rows[0][1]} if rows else "Policy not found."

# --- 3. Agent & Report Orchestration ---

report_instruction = (
    "You are a Senior Strategic Growth Agent. Your goal is to provide data-backed recommendations "
    "by analyzing 'Behavioral Twins' in the Spanner Context Graph.\n\n"
    "IMPORTANT: When identifying a customer's 'current_signal', "
    "you MUST map it to one of these exact categories: [LOW_ADOPTION, BUDGET_CONSTRAINTS, COMPETITOR_THREAT].\n\n"
    
    "MISSION:\n"
    "1. FIRST: Use 'get_customer_info' to find the customer's Industry, Tier, and 'current_signal'.\n"
    "2. SECOND: Use 'check_retention_history' using that specific Industry, Tier, and Signal with the EXACT Signal name (e.g., 'LOW_ADOPTION'). "
    "   This identifies the 'Success Pathway' for that specific problem.\n"
    "3. THIRD: Retrieve policy 'POL-444' for governance.\n"
    "4. Finally: Synthesize a 'Success Blueprint' based on the highest-ROI historical path by comparing the current customer's situation "
    "   to the successful 'Behavioral Twin' found in the graph.\n\n"
    
    "REPORT STRUCTURE:\n"
    "==================================================\n"
    "🔍 CUSTOMER GROWTH INTELLIGENCE REPORT\n"
    "Account: [Customer Name/ID]| Signal: [current_signal]\n\n"
    "⚠️ HISTORICAL FRICTION (The 'What to Avoid')\n"
    "Identify a pattern where a specific action led to a 'Churned' outcome in this segment.\n\n"
    "✅ THE SUCCESS PATHWAY (The 'Institutional Wisdom')\n"
    "Detail a successful 'Renewed' path from a similar customer, explaining the reasoning used.\n\n"
    "🛡️ GOVERNED RECOMMENDATION\n"
    "Provide the final recommendation, citing the relevant Policy Rule.\n"
    "=================================================="
)

# Add it to your tools list
tools = [
    FunctionTool(get_customer_info),
    FunctionTool(check_retention_history), # The industry-based one we just made
    FunctionTool(get_policy_details)
]

async def main():
    agent = Agent(name="growth_strategist", model="gemini-2.0-flash", 
                  instruction=report_instruction, tools=tools)
    session_service = InMemorySessionService()
    await session_service.create_session(app_name=APP_NAME, user_id=USER_ID, session_id=SESSION_ID)
    runner = Runner(app_name="GrowthApp", agent=agent, session_service=session_service)

    # User Query triggers the Agent to look up the specific customer we ingested (CUST-101)
    prompt = "Generate a growth report for CUST-101. Should we offer a discount to stop their usage drop?"
    content = types.Content(role='user', parts=[types.Part(text=prompt)])

<<<<<<< HEAD
    async for event in runner.run_async(new_message=content, user_id=USER_ID, session_id=SESSION_ID):
=======
    async for event in runner.run_async(new_message=content, user_id="user_123", session_id="session_final"):
>>>>>>> upstream/main
        # 1. Capture and print the final text response
        if event.is_final_response():
            final_text = event.content.parts[0].text
            print(f"\n[Growth Strategist]:\n{final_text}")
        # 2. OPTIONAL: Print tool calls so you know the Agent is working
        elif event.content and event.content.parts:
            for part in event.content.parts:
                if part.function_call:
                    print(f"🛠️  Agent calling tool: {part.function_call.name}...")


if __name__ == "__main__":
    asyncio.run(main())
