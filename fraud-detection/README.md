# End-to-End AML & Financial Fraud Detection Workshop (Cloud Spanner Graph + BigQuery + 2-Hop GraphSAGE GNN + Managed Spanner MCP)

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/GoogleCloudPlatform/cloud-spanner-samples/blob/main/fraud-detection/assets/fraud_detection_workshop.ipynb)

This sample ([https://github.com/GoogleCloudPlatform/cloud-spanner-samples/tree/main/fraud-detection](https://github.com/GoogleCloudPlatform/cloud-spanner-samples/tree/main/fraud-detection)) provides a complete, hands-on **Anti-Money Laundering (AML) & Financial Crime Investigation** workshop unifying **BigQuery** (Reverse ETL `EXPORT DATA` and Zero-ETL `EXTERNAL_DATASET` federation), **Cloud Spanner Graph (`FraudGraph`)** (6 ISO GQL AML typologies and interactive student challenges), **2-Hop Heterogeneous GraphSAGE Graph Neural Networks (128D embeddings & `COSINE_DISTANCE` search)**, and the **Google-Managed Cloud Spanner Remote MCP Server (`https://spanner.googleapis.com/mcp`)** for agentic compliance triage.

---

## 1. Architecture & Hybrid Data Flow

The diagram below illustrates how synthetic payment network data flows from this GitHub repository through Google Cloud Storage and BigQuery into **Cloud Spanner Graph (`FraudGraph`)**, and how analytical (`%%bqsql`), graph (`%%spanner_graph`), GNN (`dgf_gnn_pipeline.py`), and agentic (**Cloud Spanner Managed MCP Server `https://spanner.googleapis.com/mcp`**) workloads operate over a single operational graph.

```mermaid
flowchart LR
    subgraph Repo["GitHub Repository (cloud-spanner-samples/fraud-detection)"]
        CSV["data/*.csv\n(6 Node & Edge CSVs)"]
        Assets["assets/*\n(schema.sql, .py, .json, .md)"]
    end

    subgraph Storage["Google Cloud Storage"]
        GCS["gs://<YOUR_BUCKET>/data/*.csv"]
    end

    subgraph BQ["BigQuery (Analytical Engine)"]
        BQStage["Staging Dataset\n<YOUR_PROJECT_ID>.fraud_dataset"]
        BQRes["Enterprise Reservation\n(PIPELINE + QUERY Slots)"]
        BQExt["Zero-ETL External Schema\n<YOUR_PROJECT_ID>.fraud_spanner_external"]
        PCA["BigFrames ML 2D PCA\n(bigframes.ml.decomposition.PCA)"]
    end

    subgraph Spanner["Cloud Spanner Graph (Operational Engine)"]
        Nodes["Node Tables\nPerson (2,000)\nAccount (2,500 + 128d Embedding)\nDevice (1,800)"]
        Edges["Interleaved Edge Tables\nOwns (2,500)\nUsesDevice (2,518)\nTransfer (15,000)"]
        Graph["Property Graph: FraudGraph\n(ISO GQL + COSINE_DISTANCE)"]
    end

    subgraph AI["Graph ML & Managed Spanner MCP"]
        GNN["2-Hop GraphSAGE GNN\n(dgf_gnn_pipeline.py)"]
        MCP["Cloud Spanner Managed MCP Server\n(https://spanner.googleapis.com/mcp)"]
    end

    CSV -->|"Step 0.3: Auto-Sync"| GCS
    GCS -->|"Step 1: bq load"| BQStage
    Assets -->|"Step 4: gcloud spanner ddl update"| Graph
    BQStage -->|"Step 5: Reverse ETL\nEXPORT DATA (format='CLOUD_SPANNER')"| Nodes
    BQRes -.->|"Powers PIPELINE Job"| BQStage
    Nodes --- Edges
    Edges --- Graph
    Graph <-->|"Step 6: Zero-ETL\nCREATE EXTERNAL SCHEMA"| BQExt
    BQExt -->|"Step 9: Embedding Projection"| PCA
    Graph <-->|"Step 8: 2-Hop Message Passing &\n128-dim Embedding Write-Back"| GNN
    Graph <-->|"Step 10: Managed MCP JSON-RPC 2.0\n(create_session, execute_sql_readonly)"| MCP
```

---

## 2. Repository Structure

| Path | Description |
| :--- | :--- |
| [`README.md`](./README.md) | Workshop overview, architecture diagrams, example fraud graphs, Managed Spanner MCP guide, and setup instructions. |
| `assets/fraud_detection_workshop.ipynb` [![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/GoogleCloudPlatform/cloud-spanner-samples/blob/main/fraud-detection/assets/fraud_detection_workshop.ipynb) | **End-to-End Interactive Notebook (83 cells, Sections `0`–`10`)** with `# @param` UI controls, automatic GitHub file download, BigQuery staging, Reverse ETL, `EXTERNAL_DATASET`, `%%spanner_graph` visual queries, 6 Student Challenges, GNN training, 2D PCA visualization, and Cloud Spanner Managed MCP Server (`https://spanner.googleapis.com/mcp`) compliance triage. |
| [`assets/CHALLENGE_SOLUTIONS.md`](./assets/CHALLENGE_SOLUTIONS.md) | Reference `%%spanner_graph` ISO GQL solutions and verified outputs for all **6 Student Challenges**. |
| [`assets/schema.sql`](./assets/schema.sql) | Cloud Spanner DDL for `Person`, `Account` (with `embedding ARRAY<FLOAT64>(vector_length=>128)`), `Device`, interleaved `Owns`, `UsesDevice`, `Transfer`, secondary indexes, and `CREATE OR REPLACE PROPERTY GRAPH FraudGraph`. |
| [`assets/queries_gql.sql`](./assets/queries_gql.sql) | Standalone ISO GQL queries for all 6 AML fraud patterns plus BigQuery-Spanner interoperability SQL (`EXTERNAL_DATASET`, `EXTERNAL_QUERY`, and `EXPORT DATA`). |
| [`assets/dgf_gnn_pipeline.py`](./assets/dgf_gnn_pipeline.py) | 2-Hop Heterogeneous GraphSAGE GNN pipeline that extracts `FraudGraph` topology, computes 128-dimensional L2-normalized embeddings, writes them back to `Account.embedding`, and outputs evaluation metrics. |
| [`assets/gnn_evaluation_report.json`](./assets/gnn_evaluation_report.json) | Model evaluation metrics (`Recall = 1.0000`, `Precision = 0.6593`, `F1 = 0.7947`) and top-10 cosine similarity neighbors for `SHELL_CORP` aggregator `20006`. |
| [`assets/generate_fraud_data.py`](./assets/generate_fraud_data.py) | Deterministic synthetic AML payment network generator (`seed = 42`) producing the 6 CSV files in `data/`. |
| [`assets/load_spanner_data.py`](./assets/load_spanner_data.py) | Direct Spanner REST mutation loader utility (alternative to BigQuery Reverse ETL). |
| [`data/`](./data/) | Synthetic dataset CSVs: `Person.csv` (`2,000` rows), `Account.csv` (`2,500` rows), `Device.csv` (`1,800` rows), `Owns.csv` (`2,500` rows), `UsesDevice.csv` (`2,518` rows), and `Transfer.csv` (`15,000` rows). |

---

## 3. Cloud Spanner Property Graph Schema (`FraudGraph`)

`assets/schema.sql` defines 3 node tables and 3 physically interleaved edge tables inside `FraudGraph` so that outgoing edges reside on the same storage split as their parent `Person` or `Account` node:

```mermaid
graph LR
    P["👤 Person\n(person_id PK)\nkyc_tier, email, phone,\naddress, risk_score"]
    A1["🏦 Account (Source)\n(account_id PK)\naccount_type, balance, status,\nis_fraud, fraud_pattern,\nembedding FLOAT64[128]"]
    A2["🏦 Account (Destination)\n(account_id PK)\naccount_type, balance, status,\nis_fraud, fraud_pattern,\nembedding FLOAT64[128]"]
    D["📱 Device\n(device_id PK)\ndevice_type, fingerprint_hash,\nis_vpn_or_proxy, risk_score"]

    P -->|"Owns\n(INTERLEAVE IN PARENT Person)\nrole, create_time"| A1
    A1 -->|"UsesDevice\n(INTERLEAVE IN PARENT Account)\nlogin_count, ip_address"| D
    A1 -->|"Transfer\n(INTERLEAVE IN PARENT Account)\ntransfer_id, amount, transfer_type,\ncreate_time, is_flagged_tx"| A2
```

| Table Name | Graph Element | Row Count | Primary Key & Interleaving | Key Attributes |
| :--- | :--- | :--- | :--- | :--- |
| **`Person`** | `NODE` | `2,000` | `PRIMARY KEY (person_id)` | `full_name`, `email`, `phone`, `address`, `country`, `kyc_tier`, `risk_score`, `is_suspect` |
| **`Account`** | `NODE` | `2,500` | `PRIMARY KEY (account_id)` | `account_type`, `status`, `balance`, `country`, `is_fraud` (`60` fraud / `2,440` legit), `fraud_pattern`, `embedding ARRAY<FLOAT64>(vector_length=>128)` |
| **`Device`** | `NODE` | `1,800` | `PRIMARY KEY (device_id)` | `device_type`, `os_version`, `fingerprint_hash`, `ip_subnet`, `is_vpn_or_proxy`, `risk_score` |
| **`Owns`** | `EDGE` (`Person -> Account`) | `2,500` | `PRIMARY KEY (person_id, account_id)`, `INTERLEAVE IN PARENT Person` | `role`, `create_time` |
| **`UsesDevice`** | `EDGE` (`Account -> Device`) | `2,518` | `PRIMARY KEY (account_id, device_id)`, `INTERLEAVE IN PARENT Account` | `login_count`, `ip_address`, `last_used_time` |
| **`Transfer`** | `EDGE` (`Account -> Account`) | `15,000` | `PRIMARY KEY (account_id, to_account_id, transfer_id)`, `INTERLEAVE IN PARENT Account` | `amount`, `currency`, `transfer_type`, `create_time`, `step_num`, `is_flagged_tx` (`88` flagged), `fraud_pattern` |

---

## 4. Example Graphs for All 6 AML Fraud Topologies (`%%spanner_graph`)

Every fraud detection query in Section 7 of `assets/fraud_detection_workshop.ipynb` uses `%%spanner_graph` with `RETURN SAFE_TO_JSON(query_path) AS path_json, ...` to render interactive force-directed graphs alongside tabular metrics. Below are the exact topological subgraphs discovered in `FraudGraph`.

### Pattern 1: Money Mule Rings (`MONEY_MULE_RING` — Multi-Hop Fan-In / Fan-Out)

Two `SHELL_CORP` aggregator hubs (`20006` and `20012`) collect funds from mule accounts (`20001..20005` and `20007..20011`) and emulator-drained accounts (`20013..20022`), then wire consolidated payouts (`16,945.24 EUR` and `19,211.82 EUR`) to offshore sinks (`21001` and `21002`).

```mermaid
flowchart LR
    subgraph Victims["Upstream Victims (Challenge 1)"]
        V1["Account 20550\n(Loss: 4,365.22 EUR)"]
        V2["Account 20578\n(Loss: 4,257.22 EUR)"]
    end

    subgraph Mules["Feeder Mule Accounts"]
        M1["Mules 20001..20005\n(5 Accounts)"]
        M2["Mules 20007..20011\n(5 Accounts, incl. 20007 & 20011)"]
    end

    subgraph Aggregators["SHELL_CORP Aggregator Hubs"]
        A20006["🚨 Account 20006\n(SHELL_CORP)\n10 Inflows = 45,849.24 EUR"]
        A20012["🚨 Account 20012\n(SHELL_CORP)\n10 Inflows = 51,816.31 EUR"]
    end

    subgraph Sinks["Offshore Cash-Out Sinks"]
        S1["Account 21001\n(CRYPTO_RAMP: 16,945.24 EUR)"]
        S2["Account 21002\n(CRYPTO_RAMP: 19,211.82 EUR)"]
    end

    V1 -->|"4,365.22 EUR"| M2
    V2 -->|"4,257.22 EUR"| M2
    M1 -->|"SEPA_INSTANT\n(>= 2,500 EUR)"| A20006
    M2 -->|"SEPA_INSTANT\n(>= 2,500 EUR)"| A20012
    A20006 -->|"16,945.24 EUR"| S1
    A20012 -->|"19,211.82 EUR"| S2
```

---

### Pattern 2: Shared Fraudulent Devices (`SHARED_FRAUD_DEVICE` — Device-Account Collusion)

Two rooted Android `EMULATOR` devices (`30001` and `30002`, `is_vpn_or_proxy = TRUE`) are shared across `7` distinct accounts each (`20013..20017` and `20018..20022` plus ring mules `20001` and `20002`). In **Challenge 2**, tracing outbound flagged transfers from these emulator-controlled accounts reveals that they drain directly into the Pattern 1 `SHELL_CORP` hubs (`20006` and `20012`)!

```mermaid
flowchart LR
    subgraph Emulators["Rooted Android Emulators (Device)"]
        D30001["📱 Device 30001 (EMULATOR)\nhash: a669bce6...4e7c84\nVPN/Proxy: TRUE (7 Accounts)"]
        D30002["📱 Device 30002 (EMULATOR)\nhash: 27ec0009...539ec5\nVPN/Proxy: TRUE (7 Accounts)"]
    end

    subgraph Compromised["Compromised / Mule Accounts"]
        C1["Accounts 20013..20017\n+ Mule 20001"]
        C2["Accounts 20018..20022\n+ Mule 20002"]
    end

    subgraph Hubs["Cross-Pattern Link to Pattern 1 Hubs"]
        H20006["🚨 SHELL_CORP 20006"]
        H20012["🚨 SHELL_CORP 20012"]
    end

    C1 -->|"UsesDevice\n(185.220.101.*)"| D30001
    C2 -->|"UsesDevice\n(185.220.101.*)"| D30002
    C1 -->|"Flagged Drain Transfers\n(4,116–6,856 EUR)"| H20006
    C2 -->|"Flagged Drain Transfers\n(5,259–6,941 EUR)"| H20012
```

---

### Pattern 3: Structuring & Smurfing (`STRUCTURING_SMURFING` — Sub-10,000 EUR CTR Evasion)

Two groups of 4 smurf accounts (`20023..20026` and `20028..20031`) each execute 2 deposits structured just below the `10,000 EUR` regulatory reporting threshold (`9,222.40 EUR` to `9,906.20 EUR`) into collector accounts `20027` (`76,158.71 EUR` total) and `20032` (`75,473.09 EUR` total), which then wire out `75,397.12 EUR` and `74,718.36 EUR` to external beneficiaries `21010` and `21011`.

```mermaid
flowchart LR
    subgraph Owners["Smurf KYC Owners (Challenge 3)"]
        P1["Persons 10023..10026\n(4 Owners)"]
        P2["Persons 10028..10031\n(4 Owners)"]
    end

    subgraph Smurfs["Smurf Feeder Accounts"]
        S1["Accounts 20023..20026\n(8 Structured Tx: 9,227–9,824 EUR)"]
        S2["Accounts 20028..20031\n(8 Structured Tx: 9,222–9,906 EUR)"]
    end

    subgraph Collectors["Collector Hubs"]
        C20027["🚨 Collector 20027\nInflow: 76,158.71 EUR"]
        C20032["🚨 Collector 20032\nInflow: 75,473.09 EUR"]
    end

    subgraph Beneficiaries["External Beneficiaries"]
        B21010["Account 21010\n(Wire: 75,397.12 EUR)"]
        B21011["Account 21011\n(Wire: 74,718.36 EUR)"]
    end

    P1 -->|"Owns"| S1
    P2 -->|"Owns"| S2
    S1 -->|"9,000–9,999.99 EUR\n(Sub-CTR Threshold)"| C20027
    S2 -->|"9,000–9,999.99 EUR\n(Sub-CTR Threshold)"| C20032
    C20027 -->|"WIRE 75,397.12 EUR"| B21010
    C20032 -->|"WIRE 74,718.36 EUR"| B21011
```

---

### Pattern 4: Rapid Cycling (`RAPID_CYCLING` — High-Velocity Pass-Through Accounts)

Ten pass-through accounts (`20033..20042`) receive large transfers (`16,182.13 EUR` to `27,422.47 EUR`) and forward `96.81%–98.06%` of the funds within **`5` to `11` minutes**, skimming a `1.9%–3.2%` laundering fee (**Challenge 4**).

```mermaid
flowchart LR
    Src42["Source Account 20609"] -->|"Inflow: 27,422.47 EUR\n(t_in)"| PT42["🚨 Pass-Through 20042\nDwell Time: 5 min\nSkimmed Fee: 738.55 EUR (97.31%)"]
    PT42 -->|"Outflow: 26,683.92 EUR\n(t_out = t_in + 5 min)"| Dst42["Destination Account 20709"]

    Src36["Source Account 20603"] -->|"Inflow: 26,645.14 EUR\n(t_in)"| PT36["🚨 Pass-Through 20036\nDwell Time: 8 min\nSkimmed Fee: 711.61 EUR (97.33%)"]
    PT36 -->|"Outflow: 25,933.53 EUR\n(t_out = t_in + 8 min)"| Dst36["Destination Account 20703"]
```

---

### Pattern 5: Mule Networks & Identity Reuse (`IDENTITY_REUSE_MULE` — Shared PII + VPN Rings)

Eight "separate" `UNVERIFIED` `Person` records covertly reuse identical KYC contact attributes (`phone`, `email`, `address`) across two 4-account syndicates while routing `5,843.86–8,771.43 EUR` around their accounts and logging in from the same VPN devices (`30003` and `30004`, **Challenge 5**).

```mermaid
flowchart TB
    subgraph Alpha["Alpha Syndicate Ring (Shared Phone: +34-699-888-777 | Email: syndicate.alpha.ops@proton-fin.eu)"]
        PA["Persons 10043..10046\n(4 Synthetic Identities)"] -->|"Owns"| AA["🚨 Accounts 20043 -> 20044 -> 20045 -> 20046 -> 20043\n(Transfers: 5,843.86–7,936.26 EUR)"]
        AA -->|"UsesDevice (10.88.77.10..13)"| DA["📱 VPN Device 30003\n(MOBILE_ANDROID)"]
    end

    subgraph Beta["Beta Syndicate Ring (Shared Phone: +33-677-555-444 | Email: syndicate.beta.ops@proton-fin.eu)"]
        PB["Persons 10047..10050\n(4 Synthetic Identities)"] -->|"Owns"| AB["🚨 Accounts 20047 -> 20048 -> 20049 -> 20050 -> 20047\n(Transfers: 8,098.67–8,771.43 EUR)"]
        AB -->|"UsesDevice (10.88.77.10..13)"| DB["📱 VPN Device 30004\n(MOBILE_IOS)"]
    end
```

---

### Pattern 6: Circular & Loop Fund Flows (`CIRCULAR_LOOP` — 3-to-4 Hop Variable-Length Cycles)

Using the quantified path pattern `(origin:Account)-[t:Transfer WHERE t.amount >= 10000.0]->{3, 4}(origin:Account)` and chronological origin filtering (`t[0].create_time < t[ARRAY_LENGTH(t) - 1].create_time`, **Challenge 6**), `FraudGraph` isolates one 4-hop round-tripping ring (`20051..20054`) and two 3-hop triangles (`20055..20057` and `20058..20060`):

```mermaid
flowchart LR
    subgraph Loop4["4-Hop Round-Tripping Ring (Origin: 20051 | Attrition: 642.76 EUR)"]
        A51["🚨 Origin 20051"] -->|"14,500.00 EUR"| A52["Account 20052"]
        A52 -->|"14,282.50 EUR"| A53["Account 20053"]
        A53 -->|"14,068.26 EUR"| A54["Account 20054"]
        A54 -->|"13,857.24 EUR"| A51
    end

    subgraph Loop3A["3-Hop Ring 1 (Origin: 20058 | Attrition: 678.87 EUR)"]
        A58["🚨 Origin 20058"] -->|"22,800.00 EUR"| A59["Account 20059"]
        A59 -->|"22,458.00 EUR"| A60["Account 20060"]
        A60 -->|"22,121.13 EUR"| A58
    end

    subgraph Loop3B["3-Hop Ring 2 (Origin: 20055 | Attrition: 571.68 EUR)"]
        A55["🚨 Origin 20055"] -->|"19,200.00 EUR"| A56["Account 20056"]
        A56 -->|"18,912.00 EUR"| A57["Account 20057"]
        A57 -->|"18,628.32 EUR"| A55
    end
```

---

## 5. Predictive Graph ML — 2-Hop GNN Embeddings & `COSINE_DISTANCE`

`assets/dgf_gnn_pipeline.py` trains a **2-Hop Heterogeneous GraphSAGE** model over `FraudGraph`, writing `128`-dimensional L2-normalized vectors into `Account.embedding` (`ARRAY<FLOAT64>(vector_length=>128)`):
- **Node Classification Performance**: **`Recall = 1.0000` (`60 / 60` fraudulent accounts detected across all 6 patterns)**, **`Precision = 0.6593`**, **`F1 = 0.7947`** (`TP = 60`, `FP = 31`, `TN = 2,409`, `FN = 0`).
- **Structural `COSINE_DISTANCE` Discovery**: Querying `1.0 - COSINE_DISTANCE(seed.embedding, candidate.embedding)` for Money Mule Ring `SHELL_CORP` hub **`20006`** identifies **`20012`** (the counterpart `SHELL_CORP` hub of the second Money Mule Ring) as its **#1 nearest neighbor (`0.9573` cosine similarity)**—even though `20006` and `20012` share zero direct transfers!

```mermaid
flowchart LR
    Seed["🚨 Seed Account 20006\n(SHELL_CORP — Ring 1 Hub)\n128-dim GNN Embedding"]
    Match1["🎯 #1 Match: Account 20012\n(SHELL_CORP — Ring 2 Hub)\nCosine Similarity: 0.9573"]
    Match2["#2 Match: Account 20043\n(IDENTITY_REUSE_MULE)\nCosine Similarity: 0.7793"]
    Match3["#3 Match: Account 20044\n(IDENTITY_REUSE_MULE)\nCosine Similarity: 0.7793"]

    Seed <==>|"1 - COSINE_DISTANCE = 0.9573\n(Zero Direct Edges — Pure Structural Match)"| Match1
    Seed -.->|"Cosine Similarity = 0.7793"| Match2
    Seed -.->|"Cosine Similarity = 0.7793"| Match3
```

---

## 6. Agentic Fraud Investigation with Cloud Spanner Managed MCP Server (`https://spanner.googleapis.com/mcp`)

Instead of deploying or maintaining a custom MCP proxy, this workshop connects directly to the **Google-Managed Cloud Spanner Remote MCP Server (`https://spanner.googleapis.com/mcp`)** over HTTPS JSON-RPC 2.0.

- **Zero-Infrastructure Enablement**: Automatically enabled when `spanner.googleapis.com` is enabled (`gcloud services enable spanner.googleapis.com`).
- **IAM Governance**: Controlled via `roles/mcp.toolUser` and `roles/spanner.databaseReader`.
- **16 Native Managed Tools**: Includes `list_instances`, `list_databases`, `get_database_ddl`, `create_session`, `execute_sql_readonly`, `get_query_plan`, `execute_sql`, and `commit`.
- **MCP Client Configuration (`settings.json` / `gemini-extension.json`)**:
  ```json
  {
    "mcpServers": {
      "spanner": {
        "httpUrl": "https://spanner.googleapis.com/mcp",
        "authProviderType": "google_credentials",
        "oauth": {
          "scopes": ["https://www.googleapis.com/auth/cloud-platform"]
        },
        "headers": {
          "x-goog-user-project": "YOUR_PROJECT_ID"
        },
        "timeout": 30000
      }
    }
  }
  ```
- **Multi-Modal Compliance Dossier Generation (Section 10)**: Using `create_session` followed by parameterized `execute_sql_readonly` calls (`@aid = 20006`), the agent queries `GRAPH FraudGraph` (1-hop KYC ownership, linked devices, and 2-hop fund flows) alongside `COSINE_DISTANCE(seed.embedding, other.embedding)` over the 128-dim GraphSAGE vectors to surface counterpart `SHELL_CORP` hub `20012` (`0.9573` cosine similarity).

---

## 7. Quickstart: Running the Workshop Notebook in Google Colab

1. Open the notebook in Google Colab: [![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/GoogleCloudPlatform/cloud-spanner-samples/blob/main/fraud-detection/assets/fraud_detection_workshop.ipynb)
2. In **Section 0 (`0.1 Workshop Configuration Parameters`)**, fill in the `# @param` form fields:
   - `PROJECT_ID`: Your Google Cloud project ID.
   - `REGION`: `europe-west1` (default).
   - `BUCKET_NAME`: Your Google Cloud Storage bucket name for staging CSV data.
   - `GITHUB_REPO`: `https://github.com/GoogleCloudPlatform/cloud-spanner-samples/tree/main/fraud-detection` (default).
3. Run **Section 0 (`Step 0.2` & `Step 0.3`)** to authenticate, enable required GCP APIs, automatically download all workshop assets (`assets/*`) and synthetic CSVs (`data/*.csv`) from this GitHub repository, and stage `data/*.csv` to `gs://{BUCKET_NAME}/data/`.
4. Execute **Sections `1` through `10`** sequentially to complete the BigQuery staging, Spanner setup, DDL creation, Reverse ETL (`EXPORT DATA`), `EXTERNAL_DATASET` federation, all 6 `%%spanner_graph` fraud patterns & student challenges, 2-hop GNN training, 2D PCA visualization, and Cloud Spanner Managed MCP Server (`https://spanner.googleapis.com/mcp`) compliance triage.
