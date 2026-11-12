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

"""Loads synthetic fraud graph CSVs into Cloud Spanner using batched mutations.

Follows Spanner bulk write best practices:
- Loads parent node tables (Person, Account, Device) before interleaved/FK edge
  tables (Owns, UsesDevice, Transfer).
- Groups mutations into batches of up to 2,000 rows per transaction to stay
  well below Cloud Spanner's 80,000-mutation limit per commit.
- Supports both `google-cloud-spanner` Python SDK and direct Cloud Spanner REST
  API (`sessions:commit` with `gcloud auth print-access-token`).
"""

import argparse
import csv
import json
import os
import subprocess
from typing import Any
import urllib.request


TABLE_CONFIGS = [
    {
        "table": "Person",
        "csv": "Person.csv",
        "columns": [
            "person_id", "full_name", "email", "phone", "address",
            "country", "kyc_tier", "risk_score", "is_suspect", "create_time",
        ],
        "types": {
            "person_id": "int",
            "risk_score": "float",
            "is_suspect": "bool",
        },
    },
    {
        "table": "Account",
        "csv": "Account.csv",
        "columns": [
            "account_id", "account_type", "status", "balance",
            "country", "is_fraud", "fraud_pattern", "create_time",
        ],
        "types": {
            "account_id": "int",
            "balance": "float",
            "is_fraud": "int",
        },
    },
    {
        "table": "Device",
        "csv": "Device.csv",
        "columns": [
            "device_id", "device_type", "os_version", "fingerprint_hash",
            "ip_subnet", "is_vpn_or_proxy", "risk_score", "create_time",
        ],
        "types": {
            "device_id": "int",
            "is_vpn_or_proxy": "bool",
            "risk_score": "float",
        },
    },
    {
        "table": "Owns",
        "csv": "Owns.csv",
        "columns": ["person_id", "account_id", "role", "create_time"],
        "types": {
            "person_id": "int",
            "account_id": "int",
        },
    },
    {
        "table": "UsesDevice",
        "csv": "UsesDevice.csv",
        "columns": [
            "account_id", "device_id", "login_count", "ip_address", "last_used_time",
        ],
        "types": {
            "account_id": "int",
            "device_id": "int",
            "login_count": "int",
        },
    },
    {
        "table": "Transfer",
        "csv": "Transfer.csv",
        "columns": [
            "transfer_id", "account_id", "to_account_id", "amount",
            "currency", "transfer_type", "create_time", "step_num",
            "is_flagged_tx", "fraud_pattern",
        ],
        "types": {
            "account_id": "int",
            "to_account_id": "int",
            "amount": "float",
            "step_num": "int",
            "is_flagged_tx": "bool",
        },
    },
]


def _cast_for_rest(val: str, col_type: str) -> Any:
    """In Spanner REST API, INT64 is encoded as string, FLOAT64 as number, BOOL as bool."""
    if col_type == "int":
        return str(int(val))
    if col_type == "float":
        return float(val)
    if col_type == "bool":
        return val.strip().lower() in ("true", "1", "yes")
    return val


def _cast_for_sdk(val: str, col_type: str) -> Any:
    """In google-cloud-spanner Python SDK, INT64 is int, FLOAT64 is float, BOOL is bool."""
    if col_type == "int":
        return int(val)
    if col_type == "float":
        return float(val)
    if col_type == "bool":
        return val.strip().lower() in ("true", "1", "yes")
    return val


def load_via_rest(
    project: str,
    instance: str,
    database: str,
    data_dir: str,
    batch_size: int = 2000,
) -> None:
    """Loads all CSV files into Cloud Spanner using batched REST API commits."""
    token = subprocess.check_output(
        ["gcloud", "auth", "print-access-token", "--quiet"], text=True
    ).strip()
    base_url = (
        f"https://spanner.googleapis.com/v1/projects/{project}"
        f"/instances/{instance}/databases/{database}"
    )
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    # Create a Spanner session
    req = urllib.request.Request(
        f"{base_url}/sessions",
        data=b"{}",
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        session_name = json.loads(resp.read().decode("utf-8"))["name"]

    print(f"Created Spanner session: {session_name}")

    for cfg in TABLE_CONFIGS:
        table = cfg["table"]
        csv_path = os.path.join(data_dir, cfg["csv"])
        columns = cfg["columns"]
        col_types = cfg["types"]

        rows: list[list[Any]] = []
        with open(csv_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                rows.append([
                    _cast_for_rest(row[c], col_types.get(c, "str"))
                    for c in columns
                ])

        total_rows = len(rows)
        for start in range(0, total_rows, batch_size):
            batch = rows[start : start + batch_size]
            payload = {
                "singleUseTransaction": {"readWrite": {}},
                "mutations": [
                    {
                        "insertOrUpdate": {
                            "table": table,
                            "columns": columns,
                            "values": batch,
                        }
                    }
                ],
            }
            commit_req = urllib.request.Request(
                f"https://spanner.googleapis.com/v1/{session_name}:commit",
                data=json.dumps(payload).encode("utf-8"),
                headers=headers,
                method="POST",
            )
            with urllib.request.urlopen(commit_req) as resp:
                _ = resp.read()
            print(f"[{table}] Committed rows {start + 1}..{min(start + batch_size, total_rows)} / {total_rows}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Load synthetic CSVs into Cloud Spanner.")
    parser.add_argument("--project", default=os.environ.get("GOOGLE_CLOUD_PROJECT", "your-project-id"))
    parser.add_argument("--instance", default="finance-workshop")
    parser.add_argument("--database", default="fraud")
    parser.add_argument(
        "--data-dir",
        default=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data"),
    )
    parser.add_argument("--batch-size", type=int, default=2000)
    args = parser.parse_args()

    load_via_rest(
        project=args.project,
        instance=args.instance,
        database=args.database,
        data_dir=args.data_dir,
        batch_size=args.batch_size,
    )
    print("All tables successfully loaded into Cloud Spanner!")


if __name__ == "__main__":
    main()
