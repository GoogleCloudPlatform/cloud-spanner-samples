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

"""Synthetic Payment Network & AML Fraud Topology Simulator for Spanner Graph.

Generates a realistic time-stepped payment network with a tunable percentage
(~1-3%, default 2.4%) of injected Anti-Money Laundering (AML) and financial
fraud topologies:
  1. MONEY_MULE_RING: Clustered mule accounts funneling funds to aggregators.
  2. SHARED_FRAUD_DEVICE: Unrelated identities/accounts sharing compromised devices.
  3. STRUCTURING_SMURFING: Sub-$10k structured deposits followed by consolidation.
  4. RAPID_CYCLING: High-velocity pass-through accounts with <15 min dwell time.
  5. IDENTITY_REUSE_MULE: Disconnected account holders sharing PII (phone/email/address).
  6. CIRCULAR_LOOP: 3-hop and 4-hop cyclical layering flows (A -> B -> C -> D -> A).

Outputs CSV files for nodes (Person, Account, Device) and edges
(Owns, UsesDevice, Transfer) in work/fraud/data/.
"""

import argparse
import csv
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
import random
from typing import Any


COUNTRIES = ["ES", "FR", "DE", "NL", "IT", "BE", "IE", "PT", "GB", "CH"]
ACCOUNT_TYPES = ["CHECKING", "SAVINGS", "MERCHANT"]
KYC_TIERS = ["STANDARD", "ENHANCED", "UNVERIFIED"]
DEVICE_TYPES = ["MOBILE_IOS", "MOBILE_ANDROID", "DESKTOP_WEB"]
OS_VERSIONS = ["iOS 18.1", "Android 15", "macOS 15.1", "Windows 11", "Linux x86_64"]
TRANSFER_TYPES = ["SEPA_INSTANT", "WIRE", "ACH", "INTERNAL"]

FIRST_NAMES = [
    "Carlos", "Lucia", "Mateo", "Sofia", "Hugo", "Martina", "Alejandro",
    "Valeria", "Daniel", "Paula", "Pablo", "Julia", "Alvaro", "Emma",
    "Adrian", "Carmen", "David", "Laura", "Javier", "Elena", "Marc",
    "Claire", "Thomas", "Camille", "Lukas", "Hannah", "Maximilian", "Mia",
    "Liam", "Noah", "Olivia", "Amelia", "Marco", "Giulia", "Luca", "Sara",
]
LAST_NAMES = [
    "Garcia", "Fernandez", "Gonzalez", "Rodriguez", "Lopez", "Martinez",
    "Sanchez", "Perez", "Gomez", "Martin", "Dubois", "Laurent", "Moreau",
    "Mueller", "Schmidt", "Schneider", "Fischer", "Weber", "Rossi", "Russo",
    "Ferrari", "Esposito", "Bianchi", "De Vries", "Jansen", "Bakker",
    "Silva", "Santos", "Ferreira", "Costa", "Smith", "Taylor", "Brown",
]


def _ts(dt: datetime) -> str:
    """Formats a datetime as an ISO-8601 UTC timestamp compatible with BQ & Spanner."""
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def _fingerprint(seed_str: str) -> str:
    return hashlib.sha256(seed_str.encode("utf-8")).hexdigest()[:32]


def generate_dataset(
    output_dir: str,
    num_persons: int = 2000,
    num_accounts: int = 2500,
    num_devices: int = 1800,
    num_steps: int = 720,
    fraud_rate: float = 0.024,
    seed: int = 42,
) -> dict[str, Any]:
    """Generates synthetic nodes and edges with injected AML topologies."""
    rng = random.Random(seed)
    os.makedirs(output_dir, exist_ok=True)

    base_time = datetime(2026, 9, 1, 0, 0, 0, tzinfo=timezone.utc)
    tx_start_time = datetime(2026, 10, 1, 0, 0, 0, tzinfo=timezone.utc)

    target_fraud_accounts = max(60, int(round(num_accounts * fraud_rate)))

    persons: dict[int, dict[str, Any]] = {}
    accounts: dict[int, dict[str, Any]] = {}
    devices: dict[int, dict[str, Any]] = {}
    owns_edges: dict[tuple[int, int], dict[str, Any]] = {}
    uses_device_edges: dict[tuple[int, int], dict[str, Any]] = {}
    transfers: list[dict[str, Any]] = []

    tx_counter = 1

    def next_tx_id() -> str:
        nonlocal tx_counter
        tid = f"TXN-{tx_counter:07d}"
        tx_counter += 1
        return tid

    # 1. Create baseline Persons (10001 .. 10000 + num_persons)
    for i in range(1, num_persons + 1):
        pid = 10000 + i
        fname = rng.choice(FIRST_NAMES)
        lname = rng.choice(LAST_NAMES)
        country = rng.choice(COUNTRIES)
        created = base_time - timedelta(days=rng.randint(30, 900), hours=rng.randint(0, 23))
        persons[pid] = {
            "person_id": pid,
            "full_name": f"{fname} {lname}",
            "email": f"{fname.lower()}.{lname.lower()}.{pid}@example-fin.eu",
            "phone": f"+34-600-{pid % 10000:04d}-{rng.randint(10, 99)}",
            "address": f"{rng.randint(1, 250)} Financial Ave, {country}",
            "country": country,
            "kyc_tier": rng.choices(KYC_TIERS, weights=[0.70, 0.25, 0.05])[0],
            "risk_score": round(rng.uniform(0.02, 0.28), 4),
            "is_suspect": False,
            "create_time": _ts(created),
        }

    # 2. Create baseline Devices (30001 .. 30000 + num_devices)
    for i in range(1, num_devices + 1):
        did = 30000 + i
        dtype = rng.choice(DEVICE_TYPES)
        created = base_time - timedelta(days=rng.randint(10, 600), hours=rng.randint(0, 23))
        devices[did] = {
            "device_id": did,
            "device_type": dtype,
            "os_version": rng.choice(OS_VERSIONS),
            "fingerprint_hash": _fingerprint(f"device-{did}-{seed}"),
            "ip_subnet": f"192.0.{rng.randint(10, 250)}.0/24",
            "is_vpn_or_proxy": False,
            "risk_score": round(rng.uniform(0.01, 0.22), 4),
            "create_time": _ts(created),
        }

    # 3. Create baseline Accounts (20001 .. 20000 + num_accounts)
    for i in range(1, num_accounts + 1):
        aid = 20000 + i
        country = rng.choice(COUNTRIES)
        created = base_time - timedelta(days=rng.randint(15, 700), hours=rng.randint(0, 23))
        accounts[aid] = {
            "account_id": aid,
            "account_type": rng.choices(ACCOUNT_TYPES, weights=[0.65, 0.25, 0.10])[0],
            "status": "ACTIVE",
            "balance": round(rng.uniform(1200.0, 85000.0), 2),
            "country": country,
            "is_fraud": 0,
            "fraud_pattern": "NONE",
            "create_time": _ts(created),
        }

    # Link every account to a baseline owner and device first
    person_ids = list(persons.keys())
    device_ids = list(devices.keys())
    for idx, aid in enumerate(accounts.keys()):
        pid = person_ids[idx % len(person_ids)]
        owns_edges[(pid, aid)] = {
            "person_id": pid,
            "account_id": aid,
            "role": "PRIMARY_OWNER",
            "create_time": accounts[aid]["create_time"],
        }
        did = device_ids[idx % len(device_ids)]
        uses_device_edges[(aid, did)] = {
            "account_id": aid,
            "device_id": did,
            "login_count": rng.randint(5, 120),
            "ip_address": f"192.0.{did % 200 + 10}.{aid % 250 + 1}",
            "last_used_time": _ts(tx_start_time + timedelta(hours=rng.randint(1, num_steps - 1))),
        }

    # =========================================================================
    # INJECTED AML & FRAUD TOPOLOGIES (Accounts 20001 .. 20060 = 60 accounts)
    # =========================================================================

    def mark_fraud_account(aid: int, pattern: str, status: str = "FLAGGED") -> None:
        accounts[aid]["is_fraud"] = 1
        accounts[aid]["fraud_pattern"] = pattern
        accounts[aid]["status"] = status
        # Also mark owning person as suspect
        for (p_id, a_id) in owns_edges:
            if a_id == aid:
                persons[p_id]["is_suspect"] = True
                persons[p_id]["risk_score"] = round(rng.uniform(0.78, 0.98), 4)

    # -------------------------------------------------------------------------
    # Pattern 1: MONEY_MULE_RING (Accounts 20001..20012, 12 accounts)
    # Ring A: Mules 20001..20005 -> Central Aggregator 20006
    # Ring B: Mules 20007..20011 -> Central Aggregator 20012
    # -------------------------------------------------------------------------
    mule_rings = [
        ([20001, 20002, 20003, 20004, 20005], 20006, 21001, 48),
        ([20007, 20008, 20009, 20010, 20011], 20012, 21002, 120),
    ]
    for mules, aggregator, offshore_sink, base_step in mule_rings:
        mark_fraud_account(aggregator, "MONEY_MULE_RING")
        accounts[aggregator]["account_type"] = "SHELL_CORP"
        total_collected = 0.0
        for m_idx, mule_id in enumerate(mules):
            mark_fraud_account(mule_id, "MONEY_MULE_RING")
            # External victim funds entering the mule
            victim_id = 20500 + m_idx * 7 + (0 if aggregator == 20006 else 50)
            in_amt = round(rng.uniform(3200.0, 4900.0), 2)
            in_time = tx_start_time + timedelta(hours=base_step, minutes=m_idx * 6)
            transfers.append({
                "transfer_id": next_tx_id(),
                "account_id": victim_id,
                "to_account_id": mule_id,
                "amount": in_amt,
                "currency": "EUR",
                "transfer_type": "SEPA_INSTANT",
                "create_time": _ts(in_time),
                "step_num": base_step,
                "is_flagged_tx": True,
                "fraud_pattern": "MONEY_MULE_RING",
            })
            # Mule forwards 95% to Central Aggregator within minutes
            fwd_amt = round(in_amt * 0.95, 2)
            total_collected += fwd_amt
            fwd_time = in_time + timedelta(minutes=12 + m_idx * 3)
            transfers.append({
                "transfer_id": next_tx_id(),
                "account_id": mule_id,
                "to_account_id": aggregator,
                "amount": fwd_amt,
                "currency": "EUR",
                "transfer_type": "SEPA_INSTANT",
                "create_time": _ts(fwd_time),
                "step_num": base_step,
                "is_flagged_tx": True,
                "fraud_pattern": "MONEY_MULE_RING",
            })
        # Aggregator wires out consolidated funds
        transfers.append({
            "transfer_id": next_tx_id(),
            "account_id": aggregator,
            "to_account_id": offshore_sink,
            "amount": round(total_collected * 0.98, 2),
            "currency": "EUR",
            "transfer_type": "CRYPTO_RAMP",
            "create_time": _ts(tx_start_time + timedelta(hours=base_step + 1, minutes=15)),
            "step_num": base_step + 1,
            "is_flagged_tx": True,
            "fraud_pattern": "MONEY_MULE_RING",
        })

    # -------------------------------------------------------------------------
    # Pattern 2: SHARED_FRAUD_DEVICE (Accounts 20013..20022, Devices 30001..30002)
    # 5 unrelated accounts/identities per compromised emulator device
    # -------------------------------------------------------------------------
    shared_device_groups = [
        (30001, [20013, 20014, 20015, 20016, 20017], 180),
        (30002, [20018, 20019, 20020, 20021, 20022], 240),
    ]
    for dev_id, acc_list, base_step in shared_device_groups:
        devices[dev_id]["device_type"] = "EMULATOR"
        devices[dev_id]["os_version"] = "Android 15 Rooted Emulator"
        devices[dev_id]["is_vpn_or_proxy"] = True
        devices[dev_id]["risk_score"] = 0.96
        devices[dev_id]["ip_subnet"] = "185.220.101.0/24"
        for idx, aid in enumerate(acc_list):
            mark_fraud_account(aid, "SHARED_FRAUD_DEVICE")
            uses_device_edges[(aid, dev_id)] = {
                "account_id": aid,
                "device_id": dev_id,
                "login_count": rng.randint(25, 60),
                "ip_address": f"185.220.101.{10 + idx}",
                "last_used_time": _ts(tx_start_time + timedelta(hours=base_step, minutes=idx * 4)),
            }
            # Each account controlled by the shared device executes high-velocity drain transfers
            transfers.append({
                "transfer_id": next_tx_id(),
                "account_id": aid,
                "to_account_id": 20006 if dev_id == 30001 else 20012,
                "amount": round(rng.uniform(4100.0, 7800.0), 2),
                "currency": "EUR",
                "transfer_type": "SEPA_INSTANT",
                "create_time": _ts(tx_start_time + timedelta(hours=base_step, minutes=idx * 4 + 2)),
                "step_num": base_step,
                "is_flagged_tx": True,
                "fraud_pattern": "SHARED_FRAUD_DEVICE",
            })

    # -------------------------------------------------------------------------
    # Pattern 3: STRUCTURING_SMURFING (Accounts 20023..20032, 10 accounts)
    # Multiple transfers just under $10,000 threshold ($9,100 - $9,950) into
    # collector 20027 and 20032, followed by immediate large outbound wire.
    # -------------------------------------------------------------------------
    smurf_groups = [
        ([20023, 20024, 20025, 20026], 20027, 21010, 300),
        ([20028, 20029, 20030, 20031], 20032, 21011, 360),
    ]
    for smurfs, collector, beneficiary, base_step in smurf_groups:
        mark_fraud_account(collector, "STRUCTURING_SMURFING")
        structured_sum = 0.0
        for s_idx, smurf_id in enumerate(smurfs):
            mark_fraud_account(smurf_id, "STRUCTURING_SMURFING")
            for burst in range(2):
                amt = round(rng.uniform(9150.0, 9920.0), 2)
                structured_sum += amt
                t_time = tx_start_time + timedelta(hours=base_step + burst, minutes=s_idx * 9 + 5)
                transfers.append({
                    "transfer_id": next_tx_id(),
                    "account_id": smurf_id,
                    "to_account_id": collector,
                    "amount": amt,
                    "currency": "EUR",
                    "transfer_type": "WIRE",
                    "create_time": _ts(t_time),
                    "step_num": base_step + burst,
                    "is_flagged_tx": True,
                    "fraud_pattern": "STRUCTURING_SMURFING",
                })
        # Immediate consolidation wire out
        transfers.append({
            "transfer_id": next_tx_id(),
            "account_id": collector,
            "to_account_id": beneficiary,
            "amount": round(structured_sum * 0.99, 2),
            "currency": "EUR",
            "transfer_type": "WIRE",
            "create_time": _ts(tx_start_time + timedelta(hours=base_step + 2, minutes=10)),
            "step_num": base_step + 2,
            "is_flagged_tx": True,
            "fraud_pattern": "STRUCTURING_SMURFING",
        })

    # -------------------------------------------------------------------------
    # Pattern 4: RAPID_CYCLING (Pass-Through Accounts 20033..20042, 10 accounts)
    # High-velocity inbound-to-outbound fund routing with <15 min dwell time
    # -------------------------------------------------------------------------
    for idx, pt_account in enumerate(range(20033, 20043)):
        mark_fraud_account(pt_account, "RAPID_CYCLING")
        step = 410 + idx * 5
        in_time = tx_start_time + timedelta(hours=step, minutes=10)
        out_time = in_time + timedelta(minutes=rng.randint(3, 11))  # 3-11 min dwell time!
        in_amt = round(rng.uniform(16000.0, 28000.0), 2)
        out_amt = round(in_amt * rng.uniform(0.96, 0.99), 2)
        src_acc = 20600 + idx
        dst_acc = 20700 + idx
        transfers.append({
            "transfer_id": next_tx_id(),
            "account_id": src_acc,
            "to_account_id": pt_account,
            "amount": in_amt,
            "currency": "EUR",
            "transfer_type": "SEPA_INSTANT",
            "create_time": _ts(in_time),
            "step_num": step,
            "is_flagged_tx": True,
            "fraud_pattern": "RAPID_CYCLING",
        })
        transfers.append({
            "transfer_id": next_tx_id(),
            "account_id": pt_account,
            "to_account_id": dst_acc,
            "amount": out_amt,
            "currency": "EUR",
            "transfer_type": "SEPA_INSTANT",
            "create_time": _ts(out_time),
            "step_num": step,
            "is_flagged_tx": True,
            "fraud_pattern": "RAPID_CYCLING",
        })

    # -------------------------------------------------------------------------
    # Pattern 5: IDENTITY_REUSE_MULE (Accounts 20043..20050, Persons 10043..10050)
    # Disconnected account holders reusing synthetic identity PII (phone/email/address)
    # -------------------------------------------------------------------------
    identity_clusters = [
        (
            [20043, 20044, 20045, 20046],
            "+34-699-888-777",
            "syndicate.alpha.ops@proton-fin.eu",
            "99 Offshore Quay, Suite 4B, ES",
            30003,
            490,
        ),
        (
            [20047, 20048, 20049, 20050],
            "+33-677-555-444",
            "syndicate.beta.ops@proton-fin.eu",
            "14 Rue fantome, Apt 12, FR",
            30004,
            520,
        ),
    ]
    for acc_cluster, shared_phone, shared_email, shared_addr, shared_dev, base_step in identity_clusters:
        devices[shared_dev]["is_vpn_or_proxy"] = True
        devices[shared_dev]["risk_score"] = 0.91
        for c_idx, aid in enumerate(acc_cluster):
            mark_fraud_account(aid, "IDENTITY_REUSE_MULE")
            pid = 10000 + (aid - 20000)
            persons[pid]["phone"] = shared_phone
            persons[pid]["email"] = shared_email
            persons[pid]["address"] = shared_addr
            persons[pid]["kyc_tier"] = "UNVERIFIED"
            uses_device_edges[(aid, shared_dev)] = {
                "account_id": aid,
                "device_id": shared_dev,
                "login_count": rng.randint(15, 45),
                "ip_address": f"10.88.77.{10 + c_idx}",
                "last_used_time": _ts(tx_start_time + timedelta(hours=base_step, minutes=c_idx * 5)),
            }
            next_aid = acc_cluster[(c_idx + 1) % len(acc_cluster)]
            transfers.append({
                "transfer_id": next_tx_id(),
                "account_id": aid,
                "to_account_id": next_aid,
                "amount": round(rng.uniform(5200.0, 8900.0), 2),
                "currency": "EUR",
                "transfer_type": "SEPA_INSTANT",
                "create_time": _ts(tx_start_time + timedelta(hours=base_step, minutes=15 + c_idx * 8)),
                "step_num": base_step,
                "is_flagged_tx": True,
                "fraud_pattern": "IDENTITY_REUSE_MULE",
            })

    # -------------------------------------------------------------------------
    # Pattern 6: CIRCULAR_LOOP (Accounts 20051..20060, 10 accounts)
    # Multi-hop cyclical fund flows:
    #   Loop 1 (4-hop): 20051 -> 20052 -> 20053 -> 20054 -> 20051
    #   Loop 2 (3-hop): 20055 -> 20056 -> 20057 -> 20055
    #   Loop 3 (3-hop): 20058 -> 20059 -> 20060 -> 20058
    # -------------------------------------------------------------------------
    circular_loops = [
        ([20051, 20052, 20053, 20054], 14500.0, 580),
        ([20055, 20056, 20057], 19200.0, 610),
        ([20058, 20059, 20060], 22800.0, 640),
    ]
    for loop_nodes, start_amount, base_step in circular_loops:
        amt = start_amount
        for hop_idx, src_id in enumerate(loop_nodes):
            mark_fraud_account(src_id, "CIRCULAR_LOOP")
            dst_id = loop_nodes[(hop_idx + 1) % len(loop_nodes)]
            hop_time = tx_start_time + timedelta(hours=base_step, minutes=hop_idx * 14 + 5)
            transfers.append({
                "transfer_id": next_tx_id(),
                "account_id": src_id,
                "to_account_id": dst_id,
                "amount": round(amt, 2),
                "currency": "EUR",
                "transfer_type": "WIRE",
                "create_time": _ts(hop_time),
                "step_num": base_step,
                "is_flagged_tx": True,
                "fraud_pattern": "CIRCULAR_LOOP",
            })
            amt = amt * 0.985  # slight layering fee on each hop

    # If user requested a higher fraud_rate (> 60 accounts), mark additional accounts
    extra_needed = target_fraud_accounts - 60
    for extra_idx in range(max(0, extra_needed)):
        aid = 20061 + extra_idx
        if aid in accounts:
            mark_fraud_account(aid, "MONEY_MULE_RING")

    # =========================================================================
    # LEGITIMATE BACKGROUND TIME-STEP TRANSACTIONS (~15,000 total transfers)
    # =========================================================================
    legit_accounts = [aid for aid, a in accounts.items() if a["is_fraud"] == 0]
    target_total_transfers = 15000
    remaining_transfers = max(0, target_total_transfers - len(transfers))

    for i in range(remaining_transfers):
        step = rng.randint(0, num_steps - 1)
        src_id = rng.choice(legit_accounts)
        dst_id = rng.choice(legit_accounts)
        if src_id == dst_id:
            dst_id = legit_accounts[(legit_accounts.index(src_id) + 1) % len(legit_accounts)]
        tx_time = tx_start_time + timedelta(
            hours=step,
            minutes=rng.randint(0, 59),
            seconds=rng.randint(0, 59),
        )
        # Normal log-normal-like payment amounts ($15 to $3,500)
        amt = round(min(4500.0, max(12.50, rng.expovariate(1.0 / 320.0))), 2)
        transfers.append({
            "transfer_id": next_tx_id(),
            "account_id": src_id,
            "to_account_id": dst_id,
            "amount": amt,
            "currency": "EUR",
            "transfer_type": rng.choices(TRANSFER_TYPES, weights=[0.50, 0.15, 0.20, 0.15])[0],
            "create_time": _ts(tx_time),
            "step_num": step,
            "is_flagged_tx": False,
            "fraud_pattern": "NONE",
        })

    # Write CSV files
    def write_csv(filename: str, fieldnames: list[str], rows: list[dict[str, Any]]) -> str:
        path = os.path.join(output_dir, filename)
        with open(path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(rows)
        return path

    files_written = {
        "Person.csv": write_csv(
            "Person.csv",
            [
                "person_id", "full_name", "email", "phone", "address",
                "country", "kyc_tier", "risk_score", "is_suspect", "create_time",
            ],
            list(persons.values()),
        ),
        "Account.csv": write_csv(
            "Account.csv",
            [
                "account_id", "account_type", "status", "balance",
                "country", "is_fraud", "fraud_pattern", "create_time",
            ],
            list(accounts.values()),
        ),
        "Device.csv": write_csv(
            "Device.csv",
            [
                "device_id", "device_type", "os_version", "fingerprint_hash",
                "ip_subnet", "is_vpn_or_proxy", "risk_score", "create_time",
            ],
            list(devices.values()),
        ),
        "Owns.csv": write_csv(
            "Owns.csv",
            ["person_id", "account_id", "role", "create_time"],
            list(owns_edges.values()),
        ),
        "UsesDevice.csv": write_csv(
            "UsesDevice.csv",
            ["account_id", "device_id", "login_count", "ip_address", "last_used_time"],
            list(uses_device_edges.values()),
        ),
        "Transfer.csv": write_csv(
            "Transfer.csv",
            [
                "transfer_id", "account_id", "to_account_id", "amount",
                "currency", "transfer_type", "create_time", "step_num",
                "is_flagged_tx", "fraud_pattern",
            ],
            transfers,
        ),
    }

    fraud_accounts_count = sum(1 for a in accounts.values() if a["is_fraud"] == 1)
    flagged_tx_count = sum(1 for t in transfers if t["is_flagged_tx"])
    pattern_breakdown: dict[str, int] = {}
    for a in accounts.values():
        p = a["fraud_pattern"]
        pattern_breakdown[p] = pattern_breakdown.get(p, 0) + 1

    summary = {
        "persons": len(persons),
        "accounts": len(accounts),
        "devices": len(devices),
        "owns_edges": len(owns_edges),
        "uses_device_edges": len(uses_device_edges),
        "transfers": len(transfers),
        "fraud_accounts": fraud_accounts_count,
        "fraud_account_percentage": round(100.0 * fraud_accounts_count / len(accounts), 2),
        "flagged_transfers": flagged_tx_count,
        "account_pattern_breakdown": pattern_breakdown,
        "files": files_written,
    }
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate synthetic AML fraud graph CSVs.")
    parser.add_argument(
        "--output-dir",
        default=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data"),
        help="Directory where CSV files will be written.",
    )
    parser.add_argument("--num-persons", type=int, default=2000)
    parser.add_argument("--num-accounts", type=int, default=2500)
    parser.add_argument("--num-devices", type=int, default=1800)
    parser.add_argument("--num-steps", type=int, default=720)
    parser.add_argument("--fraud-rate", type=float, default=0.024)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    if args.num_accounts < 1100:
        parser.error(
            "num-accounts must be at least 1100 to accommodate all hardcoded fraud topology accounts."
        )

    summary = generate_dataset(
        output_dir=args.output_dir,
        num_persons=args.num_persons,
        num_accounts=args.num_accounts,
        num_devices=args.num_devices,
        num_steps=args.num_steps,
        fraud_rate=args.fraud_rate,
        seed=args.seed,
    )
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
