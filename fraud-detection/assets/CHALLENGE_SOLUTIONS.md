# Financial Fraud Detection Workshop — Student Challenge Solutions

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/GoogleCloudPlatform/cloud-spanner-samples/blob/main/fraud-detection/assets/fraud_detection_workshop.ipynb)

This document contains the reference `%%spanner_graph` ISO GQL solutions and verified query outputs for all **6 Student Challenges** in `assets/fraud_detection_workshop.ipynb` [![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/GoogleCloudPlatform/cloud-spanner-samples/blob/main/fraud-detection/assets/fraud_detection_workshop.ipynb) (one challenge per Anti-Money Laundering / Fraud pattern).

---

## Configurable Environment Parameters

All `%%spanner_graph` cells in the notebook use the configurable variables defined in Section 0:

```python
PROJECT_ID = "your-project-id"
BQ_DATASET = "fraud_dataset"
SPANNER_INSTANCE = "finance-workshop"
SPANNER_DATABASE = "fraud"
```

---

## Challenge 1: Money Mule Rings (`MONEY_MULE_RING`) — Tracing Upstream Victim Losses

### Challenge Prompt
In Pattern 1, we identified the two `SHELL_CORP` aggregator accounts (`20006` and `20012`) and the mule accounts feeding them. Now go one hop further upstream to identify the **original victim accounts** whose funds were drained into the mule accounts:
* Match the 2-hop path `query_path = (victim:Account)-[t_victim:Transfer]->(mule:Account)-[t_mule:Transfer]->(aggregator:Account {account_type: 'SHELL_CORP'})`.
* Filter for `t_victim.amount >= 3000.0`, `t_mule.amount >= 2500.0`, and `t_mule.create_time >= t_victim.create_time`.
* Return `SAFE_TO_JSON(query_path) AS path_json`, `victim.account_id AS victim_account_id`, `mule.account_id AS mule_account_id`, `aggregator.account_id AS aggregator_account_id`, `ROUND(t_victim.amount, 2) AS victim_loss_amount`, and `ROUND(t_mule.amount, 2) AS mule_forwarded_amount`, ordered by `victim_loss_amount DESC`.
* **Question**: Which victim account suffered the largest single initial transfer loss, and which `SHELL_CORP` aggregator received those laundered funds?

### Solution Query (`%%spanner_graph`)
```sql
%%spanner_graph --project {PROJECT_ID} --instance {SPANNER_INSTANCE} --database {SPANNER_DATABASE}
GRAPH FraudGraph
MATCH query_path = (victim:Account)-[t_victim:Transfer]->(mule:Account)-[t_mule:Transfer]->(aggregator:Account {account_type: 'SHELL_CORP'})
FILTER t_victim.amount >= 3000.0
  AND t_mule.amount >= 2500.0
  AND t_mule.create_time >= t_victim.create_time
RETURN SAFE_TO_JSON(query_path) AS path_json,
       victim.account_id AS victim_account_id,
       mule.account_id AS mule_account_id,
       aggregator.account_id AS aggregator_account_id,
       ROUND(t_victim.amount, 2) AS victim_loss_amount,
       ROUND(t_mule.amount, 2) AS mule_forwarded_amount
ORDER BY victim_loss_amount DESC
```

### Verified Output & Answer
| `victim_account_id` | `mule_account_id` | `aggregator_account_id` | `victim_loss_amount` (EUR) | `mule_forwarded_amount` (EUR) |
| :--- | :--- | :--- | :--- | :--- |
| **`20550`** | **`20007`** | **`20012`** | **`4365.22`** | **`4146.96`** |
| `20578` | `20011` | `20012` | `4257.22` | `4044.36` |
| `20557` | `20008` | `20012` | `4162.17` | `3954.06` |
| `20571` | `20010` | `20012` | `3976.96` | `3778.11` |
| `20521` | `20004` | `20006` | `3961.63` | `3763.55` |
| `20564` | `20009` | `20012` | `3874.12` | `3680.41` |
| `20514` | `20003` | `20006` | `3694.04` | `3509.34` |
| `20528` | `20005` | `20006` | `3639.07` | `3457.12` |
| `20507` | `20002` | `20006` | `3473.30` | `3299.64` |
| `20500` | `20001` | `20006` | `3433.06` | `3261.41` |

* **Answer**: Victim account **`20550`** suffered the largest single initial loss (**`4,365.22 EUR`**), which was routed through mule account **`20007`** (`4,146.96 EUR`, retaining a 5% mule commission) into `SHELL_CORP` aggregator account **`20012`**.

---

## Challenge 2: Shared Fraudulent Devices (`SHARED_FRAUD_DEVICE`) — Tracing Drain Transfers from Rooted Emulators

### Challenge Prompt
In Pattern 2, we discovered two rooted Android `EMULATOR` devices (`30001` and `30002`) shared across multiple accounts. Now connect the device authentication graph to the payment transfer graph to trace where the accounts controlled by those emulators sent their funds:
* Match `query_path = (d:Device {device_type: 'EMULATOR'})<-[u:UsesDevice]-(src:Account)-[t:Transfer]->(dst:Account)`.
* Filter for flagged transactions (`t.is_flagged_tx = TRUE`).
* Return `SAFE_TO_JSON(query_path) AS path_json`, `d.device_id AS emulator_device_id`, `src.account_id AS compromised_account_id`, `dst.account_id AS recipient_account_id`, `ROUND(t.amount, 2) AS drain_amount`, and `u.ip_address AS session_ip`, ordered by `drain_amount DESC`.
* **Question**: Which two accounts are the recipients (`recipient_account_id`) of the drain transfers originating from emulator devices `30001` and `30002`, and how does this link Pattern 2 to Pattern 1?

### Solution Query (`%%spanner_graph`)
```sql
%%spanner_graph --project {PROJECT_ID} --instance {SPANNER_INSTANCE} --database {SPANNER_DATABASE}
GRAPH FraudGraph
MATCH query_path = (d:Device {device_type: 'EMULATOR'})<-[u:UsesDevice]-(src:Account)-[t:Transfer]->(dst:Account)
FILTER t.is_flagged_tx = TRUE
RETURN SAFE_TO_JSON(query_path) AS path_json,
       d.device_id AS emulator_device_id,
       src.account_id AS compromised_account_id,
       dst.account_id AS recipient_account_id,
       ROUND(t.amount, 2) AS drain_amount,
       u.ip_address AS session_ip
ORDER BY drain_amount DESC
```

### Verified Output & Answer
| `emulator_device_id` | `compromised_account_id` | `recipient_account_id` | `drain_amount` (EUR) | `session_ip` |
| :--- | :--- | :--- | :--- | :--- |
| `30002` | `20020` | **`20012`** | `7247.30` | `185.220.101.12` |
| `30002` | `20022` | **`20012`** | `7242.65` | `185.220.101.14` |
| `30002` | `20019` | **`20012`** | `6908.35` | `185.220.101.11` |
| `30001` | `20017` | **`20006`** | `6856.82` | `185.220.101.14` |
| `30001` | `20014` | **`20006`** | `6315.63` | `185.220.101.11` |
| `30001` | `20013` | **`20006`** | `6263.72` | `185.220.101.10` |
| `30002` | `20018` | **`20012`** | `5933.11` | `185.220.101.10` |
| `30001` | `20016` | **`20006`** | `5005.36` | `185.220.101.13` |
| `30002` | `20021` | **`20012`** | `4881.00` | `185.220.101.13` |
| `30001` | `20015` | **`20006`** | `4116.65` | `185.220.101.12` |
| `30002` | `20002` | **`20006`** | `3299.64` | `192.0.12.3` |
| `30001` | `20001` | **`20006`** | `3261.41` | `192.0.11.2` |

* **Answer**: All drain transfers from accounts on `EMULATOR` devices `30001` (`20013..20017`) and `30002` (`20018..20022`) flow directly into **`20006`** and **`20012`**—the exact two `SHELL_CORP` Money Mule Ring aggregators from Pattern 1!

---

## Challenge 3: Structuring & Smurfing (`STRUCTURING_SMURFING`) — Unmasking the KYC Owners Behind the Smurf Accounts

### Challenge Prompt
In Pattern 3, smurf accounts deposited sub-`10,000 EUR` (`9,000.00–9,999.99 EUR`) structured transfers into collector accounts `20027` and `20032` to evade Currency Transaction Report (CTR) thresholds. Write a `%%spanner_graph` query to unmask the `Person` identities who own those smurf accounts:
* Match `query_path = (owner:Person)-[o:Owns]->(smurf:Account)-[t:Transfer]->(collector:Account)`.
* Filter for `t.amount BETWEEN 9000.0 AND 9999.99` and `t.is_flagged_tx = TRUE`.
* Return `SAFE_TO_JSON(query_path) AS path_json`, `collector.account_id AS collector_account_id`, `smurf.account_id AS smurf_account_id`, `owner.person_id AS smurf_person_id`, `owner.full_name AS smurf_owner_name`, `owner.country AS owner_country`, `owner.risk_score AS owner_risk_score`, and `ROUND(t.amount, 2) AS structured_amount`, ordered by `collector_account_id ASC, structured_amount DESC`.
* **Question**: Which 8 `Person` identities (`smurf_person_id` and `smurf_owner_name`) own the smurf accounts feeding collector `20027` (`4` owners) and collector `20032` (`4` owners), and how many structured transfers did each smurf execute?

### Solution Query (`%%spanner_graph`)
```sql
%%spanner_graph --project {PROJECT_ID} --instance {SPANNER_INSTANCE} --database {SPANNER_DATABASE}
GRAPH FraudGraph
MATCH query_path = (owner:Person)-[o:Owns]->(smurf:Account)-[t:Transfer]->(collector:Account)
FILTER t.amount BETWEEN 9000.0 AND 9999.99
  AND t.is_flagged_tx = TRUE
RETURN SAFE_TO_JSON(query_path) AS path_json,
       collector.account_id AS collector_account_id,
       smurf.account_id AS smurf_account_id,
       owner.person_id AS smurf_person_id,
       owner.full_name AS smurf_owner_name,
       owner.country AS owner_country,
       owner.risk_score AS owner_risk_score,
       ROUND(t.amount, 2) AS structured_amount
ORDER BY collector_account_id ASC, structured_amount DESC
```

### Verified Output & Answer
| `collector_account_id` | `smurf_account_id` | `smurf_person_id` | `smurf_owner_name` | `owner_country` | `owner_risk_score` | `structured_amount` (EUR) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `20027` | `20025` | `10025` | `Mateo Esposito` | `FR` | `0.7879` | `9824.24` |
| `20027` | `20023` | `10023` | `Camille Ferreira` | `GB` | `0.9654` | `9730.43` |
| `20027` | `20023` | `10023` | `Camille Ferreira` | `GB` | `0.9654` | `9588.75` |
| `20027` | `20026` | `10026` | `Olivia Schneider` | `PT` | `0.9213` | `9583.93` |
| `20027` | `20025` | `10025` | `Mateo Esposito` | `FR` | `0.7879` | `9446.93` |
| `20027` | `20026` | `10026` | `Olivia Schneider` | `PT` | `0.9213` | `9398.68` |
| `20027` | `20024` | `10024` | `Sara Schmidt` | `CH` | `0.8381` | `9358.42` |
| `20027` | `20024` | `10024` | `Sara Schmidt` | `CH` | `0.8381` | `9227.33` |
| `20032` | `20030` | `10030` | `Carlos Martinez` | `NL` | `0.9255` | `9906.20` |
| `20032` | `20030` | `10030` | `Carlos Martinez` | `NL` | `0.9255` | `9856.44` |
| `20032` | `20029` | `10029` | `Laura Costa` | `NL` | `0.8744` | `9358.84` |
| `20032` | `20028` | `10028` | `Hannah Esposito` | `FR` | `0.8060` | `9324.10` |
| `20032` | `20031` | `10031` | `Sofia Dubois` | `IE` | `0.8571` | `9293.49` |
| `20032` | `20029` | `10029` | `Laura Costa` | `NL` | `0.8744` | `9263.77` |
| `20032` | `20028` | `10028` | `Hannah Esposito` | `FR` | `0.8060` | `9247.85` |
| `20032` | `20031` | `10031` | `Sofia Dubois` | `IE` | `0.8571` | `9222.40` |

* **Answer**:
  * Collector **`20027`** is fed by 4 smurf owners (`10023 Camille Ferreira`, `10024 Sara Schmidt`, `10025 Mateo Esposito`, `10026 Olivia Schneider`).
  * Collector **`20032`** is fed by 4 smurf owners (`10028 Hannah Esposito`, `10029 Laura Costa`, `10030 Carlos Martinez`, `10031 Sofia Dubois`).
  * Each smurf executed exactly **2** sub-threshold transfers (`16` structured transfers total).

---

## Challenge 4: Rapid Cycling (`RAPID_CYCLING`) — Quantifying Illicit Fee Skimming on Sub-10-Minute Pass-Throughs

### Challenge Prompt
High-velocity pass-through accounts (`RAPID_CYCLING`) typically skim a small fee (`1%–4%`) before forwarding funds downstream. Write a `%%spanner_graph` query to isolate ultra-fast pass-through hops that occurred in **10 minutes or less** and calculate the exact fee retained (in EUR) by each pass-through account:
* Match `query_path = (src:Account)-[t_in:Transfer]->(pt:Account)-[t_out:Transfer]->(dst:Account)`.
* Filter for `src.account_id != dst.account_id`, `t_in.amount >= 15000.0`, `TIMESTAMP_DIFF(t_out.create_time, t_in.create_time, MINUTE) BETWEEN 1 AND 10`, and `(t_out.amount / t_in.amount) BETWEEN 0.94 AND 1.00`.
* Return `SAFE_TO_JSON(query_path) AS path_json`, `pt.account_id AS passthrough_account_id`, `TIMESTAMP_DIFF(t_out.create_time, t_in.create_time, MINUTE) AS dwell_minutes`, `ROUND(t_in.amount, 2) AS inbound_amount`, `ROUND(t_out.amount, 2) AS outbound_amount`, and `ROUND(t_in.amount - t_out.amount, 2) AS skimmed_fee_eur`, ordered by `skimmed_fee_eur DESC`.
* **Question**: How many pass-through accounts forwarded funds in `<= 10` minutes, and which account skimmed the largest fee (in EUR)?

### Solution Query (`%%spanner_graph`)
```sql
%%spanner_graph --project {PROJECT_ID} --instance {SPANNER_INSTANCE} --database {SPANNER_DATABASE}
GRAPH FraudGraph
MATCH query_path = (src:Account)-[t_in:Transfer]->(pt:Account)-[t_out:Transfer]->(dst:Account)
FILTER src.account_id != dst.account_id
  AND t_in.amount >= 15000.0
  AND TIMESTAMP_DIFF(t_out.create_time, t_in.create_time, MINUTE) BETWEEN 1 AND 10
  AND (t_out.amount / t_in.amount) BETWEEN 0.94 AND 1.00
RETURN SAFE_TO_JSON(query_path) AS path_json,
       pt.account_id AS passthrough_account_id,
       TIMESTAMP_DIFF(t_out.create_time, t_in.create_time, MINUTE) AS dwell_minutes,
       ROUND(t_in.amount, 2) AS inbound_amount,
       ROUND(t_out.amount, 2) AS outbound_amount,
       ROUND(t_in.amount - t_out.amount, 2) AS skimmed_fee_eur
ORDER BY skimmed_fee_eur DESC
```

### Verified Output & Answer
| `passthrough_account_id` | `dwell_minutes` | `inbound_amount` (EUR) | `outbound_amount` (EUR) | `skimmed_fee_eur` (EUR) |
| :--- | :--- | :--- | :--- | :--- |
| **`20042`** | **`5`** | **`27422.47`** | **`26683.92`** | **`738.55`** |
| `20036` | `6` | `22919.30` | `22217.25` | `702.05` |
| `20034` | `8` | `21880.09` | `21215.33` | `664.76` |
| `20033` | `6` | `24034.02` | `23482.57` | `551.45` |
| `20038` | `7` | `26084.79` | `25569.87` | `514.92` |
| `20041` | `6` | `18133.36` | `17769.41` | `363.95` |
| `20040` | `5` | `16182.13` | `15831.91` | `350.22` |

* **Answer**: **7** pass-through accounts forwarded funds in 10 minutes or less (`5–8 minutes`), and account **`20042`** skimmed the largest fee (**`738.55 EUR`** in just **5 minutes**).

---

## Challenge 5: Mule Networks & Identity Reuse (`IDENTITY_REUSE_MULE`) — Linking Shared PII Rings to Shared VPN Devices

### Challenge Prompt
In Pattern 5, we found two synthetic identity rings (`10043..10046` and `10047..10050`) that covertly share phone numbers, emails, and physical addresses across "separate" `Person` records. Now test whether those synthetic identities also log in from the same VPN/Proxy devices:
* Match `query_path = (p:Person {kyc_tier: 'UNVERIFIED'})-[:Owns]->(a:Account)-[u:UsesDevice]->(d:Device {is_vpn_or_proxy: TRUE})`.
* Filter for `a.fraud_pattern = 'IDENTITY_REUSE_MULE'`.
* Return `SAFE_TO_JSON(query_path) AS path_json`, `d.device_id AS shared_vpn_device_id`, `p.email AS shared_syndicate_email`, `p.phone AS shared_syndicate_phone`, `p.person_id AS person_id`, `a.account_id AS account_id`, and `u.ip_address AS login_ip`, ordered by `shared_vpn_device_id ASC, account_id ASC`.
* **Question**: Which two VPN/Proxy `device_id`s are shared by the Alpha (`syndicate.alpha.ops@proton-fin.eu`) and Beta (`syndicate.beta.ops@proton-fin.eu`) identity rings, and what IP subnet (`login_ip`) do they use?

### Solution Query (`%%spanner_graph`)
```sql
%%spanner_graph --project {PROJECT_ID} --instance {SPANNER_INSTANCE} --database {SPANNER_DATABASE}
GRAPH FraudGraph
MATCH query_path = (p:Person {kyc_tier: 'UNVERIFIED'})-[:Owns]->(a:Account)-[u:UsesDevice]->(d:Device {is_vpn_or_proxy: TRUE})
FILTER a.fraud_pattern = 'IDENTITY_REUSE_MULE'
RETURN SAFE_TO_JSON(query_path) AS path_json,
       d.device_id AS shared_vpn_device_id,
       p.email AS shared_syndicate_email,
       p.phone AS shared_syndicate_phone,
       p.person_id AS person_id,
       a.account_id AS account_id,
       u.ip_address AS login_ip
ORDER BY shared_vpn_device_id ASC, account_id ASC
```

### Verified Output & Answer
| `shared_vpn_device_id` | `shared_syndicate_email` | `shared_syndicate_phone` | `person_id` | `account_id` | `login_ip` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`30003`** | `syndicate.alpha.ops@proton-fin.eu` | `+34-699-888-777` | `10043` | `20043` | `10.88.77.10` |
| **`30003`** | `syndicate.alpha.ops@proton-fin.eu` | `+34-699-888-777` | `10044` | `20044` | `10.88.77.11` |
| **`30003`** | `syndicate.alpha.ops@proton-fin.eu` | `+34-699-888-777` | `10045` | `20045` | `10.88.77.12` |
| **`30003`** | `syndicate.alpha.ops@proton-fin.eu` | `+34-699-888-777` | `10046` | `20046` | `10.88.77.13` |
| **`30004`** | `syndicate.beta.ops@proton-fin.eu` | `+33-677-555-444` | `10047` | `20047` | `10.88.77.10` |
| **`30004`** | `syndicate.beta.ops@proton-fin.eu` | `+33-677-555-444` | `10048` | `20048` | `10.88.77.11` |
| **`30004`** | `syndicate.beta.ops@proton-fin.eu` | `+33-677-555-444` | `10049` | `20049` | `10.88.77.12` |
| **`30004`** | `syndicate.beta.ops@proton-fin.eu` | `+33-677-555-444` | `10050` | `20050` | `10.88.77.13` |

* **Answer**: The Alpha syndicate (`10043..10046` / `20043..20046`) shares VPN device **`30003`**, while the Beta syndicate (`10047..10050` / `20047..20050`) shares VPN device **`30004`**, both logging in from the internal VPN subnet **`10.88.77.10–13`**.

---

## Challenge 6: Circular & Loop Fund Flows (`CIRCULAR_LOOP`) — Pinpointing Origin Anchors & Total Cycle Layering Attrition

### Challenge Prompt
In Pattern 6, every node in a 3-hop or 4-hop ring appears as a valid cycle anchor (`10` rows total across the 3 rings). However, each ring actually started at a single **originating anchor account** (`t[0].create_time < t[ARRAY_LENGTH(t) - 1].create_time`) and lost a `1.5%` layering fee at each hop. Write a `%%spanner_graph` query to isolate the **3 true originating anchor accounts** and calculate the total fee attrition (`initial_hop_amount - final_return_amount`) across each loop:
* Match `query_path = (origin:Account)-[t:Transfer WHERE t.amount >= 10000.0]->{3, 4}(origin:Account)`.
* Filter for `t[0].create_time < t[ARRAY_LENGTH(t) - 1].create_time` so only the chronological start of each loop is returned.
* Return `SAFE_TO_JSON(query_path) AS path_json`, `origin.account_id AS cycle_anchor_account_id`, `ARRAY_LENGTH(t) AS cycle_hop_length`, `ROUND(t[0].amount, 2) AS initial_hop_amount`, `ROUND(t[ARRAY_LENGTH(t) - 1].amount, 2) AS final_return_amount`, and `ROUND(t[0].amount - t[ARRAY_LENGTH(t) - 1].amount, 2) AS cycle_attrition_loss`, ordered by `cycle_attrition_loss DESC`.
* **Question**: What are the 3 true originating anchor accounts (`cycle_anchor_account_id`), and which loop lost the largest total amount to layering attrition?

### Solution Query (`%%spanner_graph`)
```sql
%%spanner_graph --project {PROJECT_ID} --instance {SPANNER_INSTANCE} --database {SPANNER_DATABASE}
GRAPH FraudGraph
MATCH query_path = (origin:Account)-[t:Transfer WHERE t.amount >= 10000.0]->{3, 4}(origin:Account)
FILTER t[0].create_time < t[ARRAY_LENGTH(t) - 1].create_time
RETURN SAFE_TO_JSON(query_path) AS path_json,
       origin.account_id AS cycle_anchor_account_id,
       ARRAY_LENGTH(t) AS cycle_hop_length,
       ROUND(t[0].amount, 2) AS initial_hop_amount,
       ROUND(t[ARRAY_LENGTH(t) - 1].amount, 2) AS final_return_amount,
       ROUND(t[0].amount - t[ARRAY_LENGTH(t) - 1].amount, 2) AS cycle_attrition_loss
ORDER BY cycle_attrition_loss DESC
```

### Verified Output & Answer
| `cycle_anchor_account_id` | `cycle_hop_length` | `initial_hop_amount` (EUR) | `final_return_amount` (EUR) | `cycle_attrition_loss` (EUR) |
| :--- | :--- | :--- | :--- | :--- |
| **`20058`** | **`3`** | **`22800.00`** | **`22121.13`** | **`678.87`** |
| **`20051`** | **`4`** | **`14500.00`** | **`13857.24`** | **`642.76`** |
| **`20055`** | **`3`** | **`19200.00`** | **`18628.32`** | **`571.68`** |

* **Answer**: The 3 chronological loop origin anchors are **`20058`** (3-hop loop `20058 -> 20059 -> 20060 -> 20058`), **`20051`** (4-hop loop `20051 -> 20052 -> 20053 -> 20054 -> 20051`), and **`20055`** (3-hop loop `20055 -> 20056 -> 20057 -> 20055`). The loop anchored at **`20058`** incurred the largest total layering attrition (**`678.87 EUR`**).
