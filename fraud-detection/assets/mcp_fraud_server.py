#!/usr/bin/env python3
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

"""Model Context Protocol (MCP) Server for Agentic Fraud Graph Investigation.

Exposes Spanner Graph (`FraudGraph`) traversal tools over stdio (JSON-RPC 2.0)
so conversational AI agents can investigate suspicious accounts, traverse
multi-hop AML topologies, and compare 128-dim GNN embeddings directly against
`projects/<YOUR_PROJECT_ID>/instances/finance-workshop/databases/fraud`.

Supported MCP Tools:
  1. `get_fraud_graph_summary`: Returns node/edge counts and fraud pattern breakdown.
  2. `detect_aml_pattern`: Runs the canonical ISO GQL query for any of the 6 AML patterns:
     - MONEY_MULE_RING
     - SHARED_FRAUD_DEVICE
     - STRUCTURING_SMURFING
     - RAPID_CYCLING
     - IDENTITY_REUSE_MULE
     - CIRCULAR_LOOP
  3. `investigate_account`: Traverses the 2-hop ego-network of an `account_id`
     (owner Person, linked Devices, inbound/outbound Transfers, and top-5 most
     similar accounts in GNN embedding space using cosine similarity).
  4. `execute_readonly_gql`: Executes a custom read-only `GRAPH FraudGraph ...`
     query against Cloud Spanner.

Usage:
  - As an MCP stdio server: `python3 assets/mcp_fraud_server.py --project <YOUR_PROJECT_ID>`
  - Quick self-test CLI: `python3 assets/mcp_fraud_server.py --project <YOUR_PROJECT_ID> --self-test`
"""

import argparse
import json
import os
import subprocess
import sys
from typing import Any
import urllib.request


PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT", "your-project-id")
INSTANCE_ID = "finance-workshop"
DATABASE_ID = "fraud"

AML_PATTERN_GQL = {
    "MONEY_MULE_RING": """
        GRAPH FraudGraph
        MATCH (mule:Account)-[t_in:Transfer]->(aggregator:Account)-[t_out:Transfer]->(sink:Account)
        FILTER mule.account_id != sink.account_id AND t_in.amount >= 2500.0 AND t_out.amount >= 10000.0
        RETURN aggregator.account_id AS aggregator_account_id,
               aggregator.account_type AS aggregator_type,
               COUNT(DISTINCT mule.account_id) AS distinct_mule_senders,
               ROUND(SUM(t_in.amount), 2) AS total_mule_inflow,
               ROUND(MAX(t_out.amount), 2) AS consolidated_outflow_amount,
               sink.account_id AS destination_sink_account_id
        GROUP BY aggregator_account_id, aggregator_type, destination_sink_account_id
        NEXT
        FILTER distinct_mule_senders >= 4
        RETURN aggregator_account_id, aggregator_type, distinct_mule_senders,
               total_mule_inflow, consolidated_outflow_amount, destination_sink_account_id
        ORDER BY total_mule_inflow DESC
    """,
    "SHARED_FRAUD_DEVICE": """
        GRAPH FraudGraph
        MATCH (p:Person)-[:Owns]->(a:Account)-[u:UsesDevice]->(d:Device)
        RETURN d.device_id AS device_id,
               d.device_type AS device_type,
               d.fingerprint_hash AS fingerprint_hash,
               d.is_vpn_or_proxy AS is_vpn_or_proxy,
               COUNT(DISTINCT a.account_id) AS shared_account_count,
               COUNT(DISTINCT p.person_id) AS distinct_person_count,
               ARRAY_AGG(DISTINCT a.account_id) AS linked_accounts
        GROUP BY device_id, device_type, fingerprint_hash, is_vpn_or_proxy
        NEXT
        FILTER shared_account_count >= 4
        RETURN device_id, device_type, fingerprint_hash, is_vpn_or_proxy,
               shared_account_count, distinct_person_count, linked_accounts
        ORDER BY shared_account_count DESC
    """,
    "STRUCTURING_SMURFING": """
        GRAPH FraudGraph
        MATCH (smurf:Account)-[t_in:Transfer]->(collector:Account)-[t_out:Transfer]->(beneficiary:Account)
        FILTER t_in.amount BETWEEN 9000.0 AND 9999.99
          AND t_out.amount >= 30000.0
          AND t_out.create_time >= t_in.create_time
        RETURN collector.account_id AS collector_account_id,
               COUNT(DISTINCT smurf.account_id) AS smurf_feeder_count,
               COUNT(t_in.transfer_id) AS structured_tx_count,
               ROUND(MIN(t_in.amount), 2) AS min_structured_deposit,
               ROUND(MAX(t_in.amount), 2) AS max_structured_deposit,
               ROUND(MAX(t_out.amount), 2) AS consolidated_wire_out,
               beneficiary.account_id AS beneficiary_account_id
        GROUP BY collector_account_id, beneficiary_account_id
        NEXT
        FILTER structured_tx_count >= 4
        RETURN collector_account_id, smurf_feeder_count, structured_tx_count,
               min_structured_deposit, max_structured_deposit, consolidated_wire_out,
               beneficiary_account_id
        ORDER BY consolidated_wire_out DESC
    """,
    "RAPID_CYCLING": """
        GRAPH FraudGraph
        MATCH (src:Account)-[t_in:Transfer]->(pt:Account)-[t_out:Transfer]->(dst:Account)
        FILTER src.account_id != dst.account_id
          AND t_in.amount >= 15000.0
          AND t_out.create_time > t_in.create_time
          AND TIMESTAMP_DIFF(t_out.create_time, t_in.create_time, MINUTE) BETWEEN 1 AND 15
          AND (t_out.amount / t_in.amount) BETWEEN 0.94 AND 1.00
        RETURN pt.account_id AS passthrough_account_id,
               src.account_id AS source_account_id,
               dst.account_id AS destination_account_id,
               t_in.amount AS inbound_amount,
               t_out.amount AS outbound_amount,
               TIMESTAMP_DIFF(t_out.create_time, t_in.create_time, MINUTE) AS dwell_time_minutes,
               ROUND((t_out.amount / t_in.amount) * 100.0, 2) AS passthrough_pct
        ORDER BY dwell_time_minutes ASC, inbound_amount DESC
    """,
    "IDENTITY_REUSE_MULE": """
        GRAPH FraudGraph
        MATCH (p1:Person)-[:Owns]->(a1:Account)-[t:Transfer]->(a2:Account)<-[:Owns]-(p2:Person)
        FILTER p1.person_id != p2.person_id
          AND (p1.phone = p2.phone OR p1.email = p2.email OR p1.address = p2.address)
        RETURN p1.person_id AS sender_person_id,
               p1.full_name AS sender_name,
               a1.account_id AS sender_account_id,
               p2.person_id AS receiver_person_id,
               p2.full_name AS receiver_name,
               a2.account_id AS receiver_account_id,
               p1.phone AS shared_phone,
               p1.email AS shared_email,
               t.amount AS transfer_amount
        ORDER BY transfer_amount DESC
    """,
    "CIRCULAR_LOOP": """
        GRAPH FraudGraph
        MATCH (origin:Account)-[t:Transfer WHERE t.amount >= 10000.0]->{3, 4}(origin:Account)
        RETURN origin.account_id AS cycle_anchor_account_id,
               ARRAY_LENGTH(t) AS cycle_hop_length,
               t[0].amount AS initial_hop_amount,
               t[ARRAY_LENGTH(t) - 1].amount AS final_return_amount,
               t[0].create_time AS cycle_start_time
        ORDER BY cycle_hop_length DESC, initial_hop_amount DESC
    """,
}


class SpannerGraphClient:
    """Lightweight Cloud Spanner SQL/GQL client using REST API."""

    def __init__(self, project: str, instance: str, database: str) -> None:
        self.project = project
        self.instance = instance
        self.database = database
        self._session_name: str | None = None

    def _token(self) -> str:
        return subprocess.check_output(
            ["gcloud", "auth", "print-access-token", "--quiet"], text=True
        ).strip()

    def _ensure_session(self, token: str) -> str:
        if self._session_name:
            return self._session_name
        url = (
            f"https://spanner.googleapis.com/v1/projects/{self.project}"
            f"/instances/{self.instance}/databases/{self.database}/sessions"
        )
        req = urllib.request.Request(
            url,
            data=b"{}",
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        with urllib.request.urlopen(req) as resp:
            self._session_name = json.loads(resp.read().decode("utf-8"))["name"]
        return self._session_name

    def query(self, sql: str) -> list[dict[str, Any]]:
        token = self._token()
        session = self._ensure_session(token)
        req = urllib.request.Request(
            f"https://spanner.googleapis.com/v1/{session}:executeSql",
            data=json.dumps({"sql": sql}).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        with urllib.request.urlopen(req) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        fields = [
            f["name"]
            for f in payload.get("metadata", {}).get("rowType", {}).get("fields", [])
        ]
        results = []
        for row in payload.get("rows", []):
            results.append(dict(zip(fields, row)))
        return results


def handle_tool_call(
    client: SpannerGraphClient, tool_name: str, arguments: dict[str, Any]
) -> dict[str, Any]:
    if tool_name == "get_fraud_graph_summary":
        patterns = client.query(
            "SELECT fraud_pattern, COUNT(*) AS account_count, "
            "ROUND(AVG(balance), 2) AS avg_balance "
            "FROM Account GROUP BY fraud_pattern ORDER BY account_count DESC"
        )
        totals = client.query(
            "SELECT "
            "(SELECT COUNT(*) FROM Person) AS total_persons, "
            "(SELECT COUNT(*) FROM Account) AS total_accounts, "
            "(SELECT COUNT(*) FROM Device) AS total_devices, "
            "(SELECT COUNT(*) FROM Transfer) AS total_transfers"
        )
        return {"totals": totals[0] if totals else {}, "patterns": patterns}

    if tool_name == "detect_aml_pattern":
        pattern = arguments.get("pattern", "MONEY_MULE_RING").upper()
        if pattern not in AML_PATTERN_GQL:
            raise ValueError(
                f"Unknown pattern '{pattern}'. Valid patterns: {list(AML_PATTERN_GQL.keys())}"
            )
        rows = client.query(AML_PATTERN_GQL[pattern])
        return {"pattern": pattern, "match_count": len(rows), "matches": rows}

    if tool_name == "investigate_account":
        aid = int(arguments["account_id"])
        profile = client.query(
            f"""
            GRAPH FraudGraph
            MATCH (p:Person)-[:Owns]->(a:Account {{account_id: {aid}}})-[:UsesDevice]->(d:Device)
            RETURN a.account_id AS account_id,
                   a.account_type AS account_type,
                   a.status AS status,
                   a.balance AS balance,
                   a.is_fraud AS is_fraud,
                   a.fraud_pattern AS fraud_pattern,
                   p.person_id AS owner_person_id,
                   p.full_name AS owner_name,
                   p.email AS owner_email,
                   p.phone AS owner_phone,
                   p.risk_score AS owner_risk_score,
                   ARRAY_AGG(d.device_id) AS device_ids
            GROUP BY account_id, account_type, status, balance, is_fraud, fraud_pattern,
                     owner_person_id, owner_name, owner_email, owner_phone, owner_risk_score
            """
        )
        recent_transfers = client.query(
            f"""
            GRAPH FraudGraph
            MATCH (a:Account {{account_id: {aid}}})-[t:Transfer]-(counterparty:Account)
            RETURN t.transfer_id AS transfer_id,
                   t.account_id AS sender_id,
                   t.to_account_id AS receiver_id,
                   t.amount AS amount,
                   t.transfer_type AS transfer_type,
                   t.create_time AS create_time,
                   t.is_flagged_tx AS is_flagged_tx,
                   t.fraud_pattern AS fraud_pattern
            ORDER BY t.amount DESC
            LIMIT 15
            """
        )
        similar_accounts = client.query(
            f"""
            SELECT target.account_id AS neighbor_account_id,
                   target.fraud_pattern AS neighbor_fraud_pattern,
                   target.status AS neighbor_status,
                   ROUND(1.0 - COSINE_DISTANCE(src.embedding, target.embedding), 4) AS gnn_cosine_similarity
            FROM Account AS src
            JOIN Account AS target ON src.account_id = {aid} AND target.account_id != {aid}
            WHERE src.embedding IS NOT NULL AND target.embedding IS NOT NULL
            ORDER BY gnn_cosine_similarity DESC
            LIMIT 5
            """
        )
        return {
            "account_profile": profile[0] if profile else {},
            "top_transfers": recent_transfers,
            "gnn_nearest_neighbors": similar_accounts,
        }

    if tool_name == "execute_readonly_gql":
        gql = arguments.get("gql", "").strip()
        if not gql.upper().startswith("GRAPH "):
            raise ValueError("Query must begin with 'GRAPH FraudGraph ...'")
        return {"rows": client.query(gql)}

    raise ValueError(f"Unknown tool: {tool_name}")


MCP_TOOLS = [
    {
        "name": "get_fraud_graph_summary",
        "description": "Returns node/edge counts and AML fraud pattern breakdown from Spanner FraudGraph.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "detect_aml_pattern",
        "description": "Runs the ISO GQL detection query for a specific AML fraud pattern on FraudGraph.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "pattern": {
                    "type": "string",
                    "enum": list(AML_PATTERN_GQL.keys()),
                }
            },
            "required": ["pattern"],
        },
    },
    {
        "name": "investigate_account",
        "description": "Investigates an account's 2-hop ego graph (owner, devices, transfers, and GNN embedding similarity).",
        "inputSchema": {
            "type": "object",
            "properties": {"account_id": {"type": "integer"}},
            "required": ["account_id"],
        },
    },
    {
        "name": "execute_readonly_gql",
        "description": "Executes a read-only ISO GQL query starting with 'GRAPH FraudGraph' on Cloud Spanner.",
        "inputSchema": {
            "type": "object",
            "properties": {"gql": {"type": "string"}},
            "required": ["gql"],
        },
    },
]


def run_stdio_server(client: SpannerGraphClient) -> None:
    """Runs the JSON-RPC 2.0 MCP server over stdin/stdout."""
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        req = json.loads(line)
        req_id = req.get("id")
        method = req.get("method")
        params = req.get("params", {})

        if method == "initialize":
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": "spanner-fraud-graph-mcp", "version": "1.0.0"},
                },
            }
        elif method == "tools/list":
            resp = {"jsonrpc": "2.0", "id": req_id, "result": {"tools": MCP_TOOLS}}
        elif method == "tools/call":
            try:
                out = handle_tool_call(
                    client, params.get("name", ""), params.get("arguments", {})
                )
                resp = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [{"type": "text", "text": json.dumps(out, indent=2)}]
                    },
                }
            except Exception as exc:  # pylint: disable=broad-except
                resp = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "isError": True,
                        "content": [{"type": "text", "text": str(exc)}],
                    },
                }
        else:
            resp = {"jsonrpc": "2.0", "id": req_id, "result": {}}

        sys.stdout.write(json.dumps(resp) + "\n")
        sys.stdout.flush()


def main() -> None:
    parser = argparse.ArgumentParser(description="MCP Server for Spanner FraudGraph.")
    parser.add_argument("--project", default=PROJECT_ID)
    parser.add_argument("--instance", default=INSTANCE_ID)
    parser.add_argument("--database", default=DATABASE_ID)
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run a live self-test of the MCP tools against Spanner FraudGraph.",
    )
    args = parser.parse_args()

    client = SpannerGraphClient(args.project, args.instance, args.database)
    if args.self_test:
        summary = handle_tool_call(client, "get_fraud_graph_summary", {})
        account_20006 = handle_tool_call(
            client, "investigate_account", {"account_id": 20006}
        )
        print(
            json.dumps(
                {"summary": summary, "investigate_account_20006": account_20006},
                indent=2,
            )
        )
    else:
        run_stdio_server(client)


if __name__ == "__main__":
    main()
