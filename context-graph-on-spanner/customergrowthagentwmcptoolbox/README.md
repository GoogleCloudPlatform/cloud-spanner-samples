**Agentic Growth Strategist (MCP Edition)**

This repository contains a System of Intelligence designed to analyze customer friction and provide governed retention recommendations. It leverages Spanner Graph for "Institutional Memory" and the Model Context Protocol (MCP) Toolbox for seamless tool integration.

**Architecture Overview**

The system follows a three-layer intelligence model:

Memory Layer: Historical decisions and outcomes stored in Spanner Graph.

Governance Layer: Corporate policies (extracted from PDFs) stored in Spanner SQL.

Action Layer: A Gemini 2.0 Agent that uses the MCP Toolbox to query both memory and rules to synthesize a recommendation.

Foundation: Google Cloud Spanner Graph — Storing the State, Event, and Policy clocks.

The Bridge: MCP Toolbox — A secure "handshake" that turns SQL/GQL queries into executable AI tools.

The Brain: Google ADK (Agent Development Kit) — Orchestrating the reasoning loop and enforcing governance.

**File Structure**

agent.py: The main orchestration script. It initializes the ToolboxSyncClient, loads the specialized toolset, and manages the Gemini 2.0 reasoning loop.

tools.yaml: The MCP configuration file. It defines the SQL and GQL (Graph Query Language) queries that the Agent uses to "see" into the database.

Setup & Installation
1. Prerequisites
A Google Cloud Project with Cloud Spanner enabled.

A Spanner database containing the MarketingContextGraph.

The MCP Toolbox server running locally or on Google Cloud Run/GCE/GKE instance.

Make sure toolbox-core is install for local deployment and testing. Version used for this code's testing: VERSION=0.31.0
pip install toolbox-core 

2. GettingStarted

Start the MCP Toolbox
Launch the toolbox server to expose your Spanner tools over HTTP:

Bash

./toolbox --tools-file "tools.yaml"
Wait for the log: INFO: Initialized 3 tools: check_retention_history, get_policy_details, get_customer_info

3. Run the Agent
In a separate terminal, execute the ADK Agent:

Bash
python3 agent.py

**How it Works: The "Behavioral Twin" Logic**

When a user asks: "Should I give CUST-101 a discount?", the agent performs the following "Chain of Thought":

Discovery: Calls get_customer_info to identify the current signal (e.g., LOW_ADOPTION).

Pattern Matching: Calls check_behavioral_success to find other Gold-tier Manufacturing customers who faced LOW_ADOPTION and successfully renewed.

Governance Check: Calls get_policy_details for POL-444 to ensure the recommended action doesn't violate margin protection rules.

Synthesis: Combines the historical success (e.g., "Strategic Workshops work better than discounts") with the policy to generate the final report.

**Example Output**

Plaintext
🔍 CUSTOMER GROWTH INTELLIGENCE REPORT
Account: CUST-101 | Signal: LOW_ADOPTION

✅ THE SUCCESS PATHWAY (The 'Institutional Wisdom')
Historically, Gold-tier accounts in Manufacturing with low adoption signals 
successfully renewed through 'Strategic Advisory Workshops' rather than discounts.

🛡️ GOVERNED RECOMMENDATION
Per Policy 'POL-444', a discount is blocked. Initiate a Strategic Advisory 
Workshop to demonstrate ROI and justify the current cost-of-service.
