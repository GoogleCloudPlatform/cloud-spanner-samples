import asyncio
import warnings
import os
from google.adk.agents import Agent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from toolbox_core import ToolboxSyncClient

# --- 1. Environment & Telemetry ---
warnings.filterwarnings("ignore", category=UserWarning)
os.environ["GOOGLE_CLOUD_SPANNER_ENABLE_METRICS"] = "false"
os.environ["OTEL_SDK_DISABLED"] = "true"

PROJECT_ID = "xxxx"
os.environ["GOOGLE_CLOUD_PROJECT"] = PROJECT_ID
os.environ["GOOGLE_CLOUD_LOCATION"] = "us-central1"
os.environ["GOOGLE_GENAI_USE_VERTEXAI"] = "True"

# --- 2. Agent Orchestration ---

report_instruction = (
    "You are a Senior Strategic Growth Agent. Your goal is to provide data-backed recommendations "
    "by analyzing 'Behavioral Twins' in the Spanner Context Graph.\n\n"
    "IMPORTANT: When identifying a customer's 'current_signal', "
    "you MUST map it to one of these exact categories: [LOW_ADOPTION, BUDGET_CONSTRAINTS, COMPETITOR_THREAT].\n\n"
    
    "MISSION:\n"
    "1. FIRST: Use 'get_customer_info' to find the customer's Industry, Tier, and 'current_signal'.\n"
    "2. SECOND: Use 'check_behavioral_success' using that specific Industry, Tier, and Signal with the EXACT Signal name (e.g., 'LOW_ADOPTION'). "
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

async def main():
    # 1. Setup Toolbox Client (Connecting to your MCP Toolbox server)
    toolbox = ToolboxSyncClient("http://127.0.0.1:5000")
    
    try:
        # Load the tools defined in your tools.yaml
        graph_tools = toolbox.load_toolset('my_graph_toolset')

        agent = Agent(
            name="growth_strategist", 
            model="gemini-2.0-flash", 
            instruction=report_instruction, 
            tools=graph_tools
        )
        
        session_service = InMemorySessionService()
        user_id = "user_123"
        session_id = "session_final"
        app_name = "GrowthApp"

        await session_service.create_session(
            app_name=app_name, 
            user_id=user_id, 
            session_id=session_id
        )
        
        runner = Runner(app_name=app_name, agent=agent, session_service=session_service)

        # User Query triggers the Agent to look up the specific customer we ingested (CUST-101)
        prompt = "Generate a growth report for CUST-101. Should we offer a discount to stop their usage drop?"
        content = types.Content(role='user', parts=[types.Part(text=prompt)])

        async for event in runner.run_async(
            new_message=content, 
            user_id=user_id, 
            session_id=session_id
        ):
            # 1. Capture and print the final text response
            if event.is_final_response():
                final_text = event.content.parts[0].text
                print(f"\n[Growth Strategist]:\n{final_text}")
            
            # 2. Print tool calls and status updates from the Agent
            elif event.content and event.content.parts:
                for part in event.content.parts:
                    if part.function_call:
                        print(f"🛠️  Agent calling toolbox: {part.function_call.name}...")

    finally:
        # Crucial: Close the toolbox connection
        toolbox.close()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nAgent stopped by user.")
