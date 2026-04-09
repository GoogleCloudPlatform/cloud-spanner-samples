**Agentic Context Ingestion**
=============================

This repository contains the Intelligence Pipeline for building an "Institutional Memory" within Google Cloud Spanner. It transforms unstructured corporate data (PDFs) and semi-structured CRM logs (CSVs) into a high-performance Property Graph.

**Folder Contents**
===================

createcontextgraph.sql: The blueprint. Contains the DDL for the Spanner schema (Customers, Decisions, Outcomes, Policies) and the MarketingContextGraph definition.

ingestpolicies.py: The Governance Ingestor. Uses Gemini to extract rules from unstructured PDF text and upserts them into the Spanner Policies table.

agent.py: The Strategic Ingestor. An agent-led script that processes CRM logs, uses Gemini to infer "Signals" and "Reasoning," and builds the causal links (Decision -> Outcome) in the Graph.

crm_history.csv: Sample data containing AE notes, revenue impact, and contract statuses.

Corporate_Retention_Policies.pdf: Sample document containing margin protection and discount rules.

**Setup & Execution**
======================

1. Database Initialization
Before running the ingestion scripts, execute the SQL in your Spanner instance to create the tables and graph structures:

Run createcontextgraph.sql in the Spanner Studio

2. Environment Configuration
   
Ensure your Google Cloud credentials and project settings are active:


export GOOGLE_CLOUD_PROJECT="your-project-id"
export GOOGLE_GENAI_USE_VERTEXAI="True"

3. Ingest Governance (PDF)
Extract corporate rules into the Governance layer:

python3 ingestpolicies.py

4. Ingest Historical Context (CSV)

Run the agentic ingestion to build the "Success Pathways" in the graph:

python3 agent.py

**The Ingestion Logic: Unstructured to Graph**
===============================================

**The pipeline follows a Upsert strategy to ensure the graph remains clean even if scripts are re-run.

**The Transformation Logic**

Extraction: Gemini reads the ae_notes or pdf_text.

Mapping: Descriptive text is mapped to structured types (e.g., "Metrics down" → LOW_ADOPTION).

Graph Construction: * Nodes: Created for each unique Customer, Decision, and Outcome.

Edges: Relationships (AboutCustomer, ResultedIn) are built to create a searchable causal chain.

The resulting Spanner Graph allows an Agent to perform "Behavioral Twin" lookups. By querying this structure, the agent can see which decisions historically led to a Renewed outcome for specific customer signals.
