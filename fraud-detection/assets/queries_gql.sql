-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- =============================================================================
-- Financial Fraud Detection Lab: ISO GQL & Hybrid BigQuery-Spanner Queries
-- Instance: finance-workshop | DB: fraud | Graph: FraudGraph
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. MONEY MULE RINGS (Pattern: MONEY_MULE_RING)
-- Detects central aggregator accounts receiving rapid transfers from >= 4
-- distinct mule accounts and wiring out consolidated funds.
-- -----------------------------------------------------------------------------
GRAPH FraudGraph
MATCH (mule:Account)-[t_in:Transfer]->(aggregator:Account)-[t_out:Transfer]->(sink:Account)
FILTER mule.account_id != sink.account_id
  AND t_in.amount >= 2500.0
  AND t_out.amount >= 10000.0
RETURN aggregator.account_id AS aggregator_account_id,
       aggregator.account_type AS aggregator_type,
       COUNT(DISTINCT mule.account_id) AS distinct_mule_senders,
       ROUND(SUM(t_in.amount), 2) AS total_mule_inflow,
       ROUND(MAX(t_out.amount), 2) AS consolidated_outflow_amount,
       sink.account_id AS destination_sink_account_id
GROUP BY aggregator_account_id, aggregator_type, destination_sink_account_id
NEXT
FILTER distinct_mule_senders >= 4
RETURN aggregator_account_id,
       aggregator_type,
       distinct_mule_senders,
       total_mule_inflow,
       consolidated_outflow_amount,
       destination_sink_account_id
ORDER BY total_mule_inflow DESC;

-- -----------------------------------------------------------------------------
-- 2. SHARED FRAUDULENT DEVICES (Pattern: SHARED_FRAUD_DEVICE)
-- Detects multiple unrelated persons/accounts authenticating from the same
-- high-risk device or emulator fingerprint hash.
-- -----------------------------------------------------------------------------
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
RETURN device_id,
       device_type,
       fingerprint_hash,
       is_vpn_or_proxy,
       shared_account_count,
       distinct_person_count,
       linked_accounts
ORDER BY shared_account_count DESC;

-- -----------------------------------------------------------------------------
-- 3. STRUCTURING & SMURFING (Pattern: STRUCTURING_SMURFING)
-- Detects multiple inbound deposits structured just below the 10,000 reporting
-- threshold (9,000 - 9,999.99) followed by large outbound consolidation.
-- -----------------------------------------------------------------------------
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
RETURN collector_account_id,
       smurf_feeder_count,
       structured_tx_count,
       min_structured_deposit,
       max_structured_deposit,
       consolidated_wire_out,
       beneficiary_account_id
ORDER BY consolidated_wire_out DESC;

-- -----------------------------------------------------------------------------
-- 4. RAPID CYCLING / PASS-THROUGH ACCOUNTS (Pattern: RAPID_CYCLING)
-- Detects high-velocity pass-through accounts where large inbound funds are
-- routed outbound within 1 to 15 minutes (near-zero dwell time) retaining <5%.
-- -----------------------------------------------------------------------------
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
ORDER BY dwell_time_minutes ASC, inbound_amount DESC;

-- -----------------------------------------------------------------------------
-- 5. MULE NETWORKS & IDENTITY REUSE (Pattern: IDENTITY_REUSE_MULE)
-- Detects disconnected Person entities owning distinct accounts that covertly
-- share personal identifiers (phone, email, or address) and transfer funds.
-- -----------------------------------------------------------------------------
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
ORDER BY transfer_amount DESC;

-- -----------------------------------------------------------------------------
-- 6. CIRCULAR & LOOP FUND FLOWS (Pattern: CIRCULAR_LOOP)
-- Uses bounded variable-length path traversal {3, 4} with inline edge pruning
-- to detect cyclical round-tripping fund flows returning to the origin account.
-- -----------------------------------------------------------------------------
GRAPH FraudGraph
MATCH (origin:Account)-[t:Transfer WHERE t.amount >= 10000.0]->{3, 4}(origin:Account)
RETURN origin.account_id AS cycle_anchor_account_id,
       ARRAY_LENGTH(t) AS cycle_hop_length,
       t[0].amount AS initial_hop_amount,
       t[ARRAY_LENGTH(t) - 1].amount AS final_return_amount,
       t[0].create_time AS cycle_start_time
ORDER BY cycle_hop_length DESC, initial_hop_amount DESC;

-- =============================================================================
-- HYBRID BIGQUERY <-> SPANNER INTEROPERABILITY PATTERNS (Run in BigQuery)
-- =============================================================================

-- Pattern A: Spanner External Dataset (Zero-ETL Linked Dataset in BigQuery)
CREATE EXTERNAL SCHEMA IF NOT EXISTS `<YOUR_PROJECT_ID>.fraud_spanner_external`
  OPTIONS (
    external_source = 'google-cloudspanner:/projects/<YOUR_PROJECT_ID>/instances/finance-workshop/databases/fraud',
    location = 'europe-west1'
  );

SELECT fraud_pattern,
       COUNT(*) AS account_count,
       ROUND(AVG(balance), 2) AS avg_balance
FROM `<YOUR_PROJECT_ID>.fraud_spanner_external.Account`
GROUP BY fraud_pattern
ORDER BY account_count DESC;

-- Pattern B: Federated Querying with EXTERNAL_QUERY (Pushing down GQL to Spanner)
SELECT *
FROM EXTERNAL_QUERY(
  'projects/<YOUR_PROJECT_ID>/locations/europe-west1/connections/fraud-spanner-conn',
  '''
  SELECT cycle_anchor_account_id,
         cycle_hop_length,
         initial_hop_amount,
         final_return_amount
  FROM GRAPH_TABLE(
    FraudGraph
    MATCH (origin:Account)-[t:Transfer WHERE t.amount >= 10000.0]->{3, 4}(origin:Account)
    COLUMNS (
      origin.account_id AS cycle_anchor_account_id,
      ARRAY_LENGTH(t) AS cycle_hop_length,
      t[0].amount AS initial_hop_amount,
      t[ARRAY_LENGTH(t) - 1].amount AS final_return_amount
    )
  )
  ORDER BY cycle_hop_length DESC, initial_hop_amount DESC
  '''
);

-- Pattern C: Reverse ETL from BigQuery to Cloud Spanner (EXPORT DATA)
EXPORT DATA OPTIONS (
  format = 'CLOUD_SPANNER',
  uri = 'https://spanner.googleapis.com/projects/<YOUR_PROJECT_ID>/instances/finance-workshop/databases/fraud',
  spanner_options = '{"table": "Account"}'
) AS
SELECT account_id,
       account_type,
       status,
       balance,
       country,
       is_fraud,
       fraud_pattern,
       create_time
FROM `<YOUR_PROJECT_ID>.fraud_dataset.Account`;
