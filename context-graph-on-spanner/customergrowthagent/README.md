**Agentic Growth Strategist (Direct Spanner Interaction)**

This repository contains a System of Intelligence designed to provide governed growth recommendations by querying a "Context Graph" stored in Google Cloud Spanner. Unlike the MCP version, this implementation uses direct Python tools to interact with Spanner SQL and GQL.

**Folder Contents**
agent.py: The main orchestration script. It uses Gemini 2.0 and the Google ADK to process customer friction through local FunctionTools.

contextgraph.sql: The Data Definition Language (DDL) for the Spanner instance, including the Customers, Decisions, Outcomes, and Policies tables, along with the MarketingContextGraph definition.

**System Architecture**
The system operates on three distinct data layers:

Temporal Identity: Basic customer metadata (Industry, Tier).

Causal Memory: A property graph linking past Decisions to their Outcomes.

Governance Layer: Legal and financial constraints (Policies) extracted from corporate documents.

Setup & Installation
1. Prerequisites
A Google Cloud Project with a Cloud Spanner instance.

The google-cloud-spanner and google-adk Python libraries.

2. Database Initialization
Execute the schema in contextgraph.sql within your Spanner instance. This creates the nodes and edges required for the Behavioral Twin lookup logic.

3. Environment Variables
Bash
export GOOGLE_CLOUD_PROJECT="your-project-id"
export GOOGLE_CLOUD_LOCATION="us-central1"
export GOOGLE_GENAI_USE_VERTEXAI="True"
4. Run the Strategist
Bash
python3 agent.py

How It Works: The Reasoning Loop
The Agent follows a strict "Chain of Thought" to ensure recommendations are data-backed and governed:

Signal Detection: The Agent queries the latest signal_type (e.g., LOW_ADOPTION) for the target customer.

Graph Traversal: Using GQL (Graph Query Language), the Agent finds "Behavioral Twins"—other accounts in the same industry/tier who faced the same signal and reached a Renewed outcome.

Policy Filtering: The Agent retrieves POL-444 (Margin Protection) to ensure the historical "Success Pathway" is still compliant with current corporate rules.

Final Synthesis: The Agent generates a report that contrasts "What to Avoid" with a "Success Blueprint."

Example Decision Logic
User Input: "Customer 101 is complaining about complexity. Should we discount?"

Agent Logic: * Step 1: Identify Signal as LOW_ADOPTION.

Step 2: Find that for LOW_ADOPTION, peer accounts CUST-105/106 succeeded via Advisory Workshops, not discounts.

Step 3: Check POL-444 which prohibits discounts for complexity-related friction.
