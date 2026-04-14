# Governed Growth Strategist: A Spanner Graph System of Intelligence

This project demonstrates how to build a **System of Intelligence** using **Google Cloud Spanner Graph** and **Gemini 2.0**. It transforms unstructured, disparate corporate data (PDFs, Slack, CRM logs) into a structured **Context Graph**, enabling AI Agents to make data-backed, governed business decisions.

---

## Repository Structure

The repository is organized into three progressive stages of implementation, moving from data ingestion to advanced agent orchestration:

### 1. Agentic Ingestion (The Institutional Memory Pipeline)
* **Goal:** Build and populate the Context Graph in Spanner.
* **Purpose:** Ingests unstructured PDF policies and CRM CSV logs.
* **Logic:** Leverages Gemini to extract causal relationships (Decision → Outcome) and corporate guardrails.
* **Key Files:**
    * `ingestpolicies.py`: Policy ingestion logic.
    * `agent.py`: The core ingestor agent.
    * `createcontextgraph.sql`: Spanner DDL for graph schema.

### 2. Customer Growth Agent (The Direct Strategist)
* **Goal:** Reasoning via Direct Database Access.
* **Purpose:** A production-ready agent that queries Spanner directly using Python `FunctionTools`.
* **Logic:** Performs "Behavioral Twin" lookups to identify successful historical patterns for specific customer profiles.
* **Key Files:**
    * `agent.py`: Logic for the growth strategist.
    * `insightsfromcontextgraph.sql`: Pre-defined queries for graph insights.

### 3. MCP Managed Integration (The Scalable Toolbox)
* **Goal:** Decoupled tooling via **Model Context Protocol (MCP)**.
* **Purpose:** Separates Agent logic from Database logic using the MCP Toolbox for Databases.
* **Logic:** Uses a `tools.yaml` configuration to map natural language intent to high-performance SQL/GQL queries.
* **Key Files:**
    * `agent.py`: Agent utilizing the MCP interface.
    * `tools.yaml`: Configuration mapping for the MCP toolbox.

---

## Technical Architecture

* **Ingestion:** Gemini 2.0 parses PDFs and CSVs, mapping them to a Property Graph schema in Spanner.
* **Storage:** **Spanner Graph** stores "Institutional Wisdom"—tracking which specific actions led to renewals versus churn.
* **Governance:** A dedicated `Policies` table acts as a real-time guardrail, ensuring AI recommendations remain within legal and financial limits.
* **Action:** The Agent synthesizes historical data and policy constraints to generate a **Success Blueprint**.

---

## Quick Start

### Prerequisites
* Google Cloud Project with an active Spanner Instance.
* Python 3.10+
* Gemini 2.0 API access (Vertex AI).

### Installation & Setup
1. **Initialize Schema:** Execute `agenticingestion/createcontextgraph.sql` in Spanner Studio.
2. **Populate Data:** Run the scripts in `agenticingestion/` to load initial policies and customer history.
3. **Deploy Agent:** Navigate to your preferred implementation folder (`customergrowthagent` or `customergrowthagentwmcptoolbox`) and run `python agent.py`.

---

## The "Behavioral Twin" Methodology

Standard RAG (Retrieval-Augmented Generation) often lacks deep relational context. This system utilizes the power of **Context Graphs** on Spanner to move beyond simple keyword searches:

* **Nodes:** `Customers`, `Decisions`, `Outcomes`, `Policies`.
* **Edges:** `ABOUT_CUSTOMER`, `RESULTED_IN`.

Instead of asking "Find manufacturing accounts," the Agent asks Spanner Graph:
> *"Show me other Gold-tier Manufacturing accounts that faced a 30% usage drop. What specific intervention did we perform, and was the outcome positive?"*

---

## License
Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0.
