**Governed Growth Strategist: A Spanner Graph System of Intelligence**
=======================================================================

This project demonstrates how to build a System of Intelligence using Google Cloud Spanner Graph and Gemini 2.0. It transforms unstructured disparate corporate data (PDFs, Slack, CRM logs) into a structured Context Graph that an AI Agent uses to make data-backed, governed business decisions.

**Repository Structure**
=========================

The repository is organized into three progressive stages of implementation:

1. **agenticingestion**/ (The Institutional Memory Pipeline)
Goal: Build the Context Graph in Spanner.

Purpose: Ingests unstructured PDF policies and CRM CSV logs.

Logic: Uses Gemini to extract causal relationships (Decision → Outcome) and corporate guardrails.

Key Files: ingestpolicies.py, agent.py (ingestor), createcontextgraph.sql.

2. **customergrowthagent**/ (The Direct Strategist)
Goal: Reasoning via Direct Database Access.

Purpose: A production-ready agent that queries Spanner directly using Python FunctionTools.

Logic: Performs "Behavioral Twin" lookups to find successful historical patterns for similar customer profile.

Key Files: agent.py, insightsfromcontextgraph.sql.

3. **customergrowthagentwmcptoolbox**/ (The Managed Integration)
Goal: Scalable Tooling via Model Context Protocol (MCP) Toolbox for Databases.

Purpose: Decouples the Agent from the Database logic using the MCP Toolbox.

Logic: Uses a tools.yaml configuration to map natural language intent to high-performance SQL/GQL queries.

Key Files: agent.py, tools.yaml.

**Technical Architecture**
==========================

Ingestion: Gemini 2.0 parses PDFs and CSVs, mapping them to a Property Graph schema in Spanner.

Storage: Spanner Graph stores "Institutional Wisdom"—which actions led to renewals and which led to churn.

Governance: The Policies table acts as a real-time guardrail, ensuring AI recommendations stay within legal/financial limits.

Action: The Agent synthesizes history + policy to generate a Success Blueprint.

Quick Start
Prerequisites
Google Cloud Project with a Spanner Instance.

Python 3.10+

Gemini 2.0 API access (Vertex AI).

Installation
Initialize Schema: Run agenticingestion/createcontextgraph.sql in Spanner Studio.

Populate Data: Run the scripts in agenticingestion/ to load your initial policies and history.

Run the Agent: Choose your preferred implementation (Direct or MCP) and run the respective agent.py.

The "Behavioral Twin" Methodology
=================================
Standard RAG (Retrieval-Augmented Generation) often lacks context. This system shows the power of Context Graph on Spanner:

Nodes: Customers, Decisions, Outcomes, Policies.

Edges: AboutCustomer, ResultedIn.

Instead of searching for keywords, the Agent asks Spanner: "Show me other Gold-tier Manufacturing accounts that faced a 30% usage drop. What did we do, and did it work?"
