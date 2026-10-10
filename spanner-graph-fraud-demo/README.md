# Spanner Graph fraud detection demo (Cymbal Bank)

A ready-to-record demo that shows how easy it is to detect a fraud ring with
Spanner Graph, all in one database:

* Full-text search and multi-hop GQL patterns, chained with `NEXT`
* Shortest paths, and the PageRank graph algorithm (Preview)
* `GRAPH_TABLE` joins with relational tables
* Multimodal vector search on profile bios and photos
* Gemini called from SQL with `ML.PREDICT`
* Natural-language questions through the Spanner remote MCP server and
  Gemini CLI

It covers the Spanner capabilities of the
[Spanner & BigQuery: Real-Time Fraud Defense Shield](../spanner-bq-fraud-defense/README.md)
codelab and more, with a people-only story: a money mule ring that runs a
fake "investment pool" scam on a P2P payments app.

The queries to run on camera, in order and with their expected results,
are in [sql/demo_queries.sql](sql/demo_queries.sql).

The setup pre-creates everything: tables, the property graph, a search index,
a Gemini remote model, and data with Gemini embeddings for every bio and
profile photo. The demo itself is only queries.

## Prerequisites

* A Google Cloud project with billing enabled, and the `gcloud` CLI.
* Permission to create Spanner instances and databases (for example,
  `roles/spanner.admin`), to enable APIs, and to create a Cloud Storage
  bucket. Project owners have all of these.
* Spanner Graph needs the **Enterprise** or **Enterprise Plus** edition.
  `setup.sh` creates a 100 processing unit Enterprise instance if the
  instance you name doesn't exist.
* To run PageRank (query 5a), the `spanner.databases.runGraphAlgorithms`
  permission, which is part of `roles/spanner.graphIntelligenceUser`.
* Optional, to ask questions in plain English: [Gemini CLI](https://github.com/google-gemini/gemini-cli)
  and the `roles/mcp.toolUser` role.

## Set up

```bash
export PROJECT_ID=your-project
./setup.sh
```

Optional settings:

| Variable | Default | Purpose |
|---|---|---|
| `SPANNER_INSTANCE` | `fraud-demo-instance` | Instance to reuse or create |
| `SPANNER_DATABASE` | `fraud-graph-db-v2` | Database to create |
| `SPANNER_CONFIG` | `regional-us-central1` | Config for a new instance |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Gemini model that `ML.PREDICT` calls |
| `GEMINI_LOCATION` | `global` | Vertex AI location of the model |
| `PHOTO_BUCKET` | `PROJECT_ID-spanner-fraud-demo` | Bucket for the profile photos, created if needed |
| `GCLOUD_ACCOUNT` | active account | Run gcloud as another account |
| `RECREATE` | `0` | Set to `1` to drop and rebuild the database |

Setup enables the Spanner, Vertex AI and Cloud Storage APIs. It takes a few
minutes and ends with row counts of 40 people, 51 transfers and 17 signals,
plus a link to Spanner Studio.

`ML.PREDICT` calls Vertex AI through the Spanner service agent, so no extra
IAM setup is needed. To use a different Gemini model, see the
[model versions](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/model-versions)
page and set `GEMINI_MODEL`.

## Ask in plain English with Gemini CLI

You can also ask questions in plain English through the managed
[Spanner remote MCP server](https://docs.cloud.google.com/spanner/docs/use-spanner-mcp)
(`https://spanner.googleapis.com/mcp`). The `gemini-cli/` folder holds a
Gemini CLI extension that connects to it with read-only tools only, plus a
context file that tells Gemini which database to query.

1. Sign in with Application Default Credentials. Gemini CLI uses them to
   call the MCP server:

   ```bash
   gcloud auth application-default login
   ```

2. Make sure that account has `roles/mcp.toolUser` and read access to the
   database (for example, `roles/spanner.databaseReader`).
3. Install the extension. It uses the same variables as `setup.sh`:

   ```bash
   ./gemini-cli/install.sh
   ```

4. Start `gemini`, run `/mcp`, and check that the `spanner` server is
   connected. Then ask, for example:
   * *Which accounts forward most of the money they receive? Only count
     accounts that received at least 5,000.*
   * *Who received the most money from accounts created in September 2026?*
   * *Which customers have no transfers at all?*

The extension only exposes `list_instances`, `list_databases`,
`get_database_ddl`, `create_session`, `execute_sql_readonly` and
`get_query_plan`, so nothing on camera can change data. To remove it, delete
`~/.gemini/extensions/spanner-fraud-demo`.

## What's inside

| Path | Contents |
|---|---|
| `sql/schema.sql` | Tables `People`, `Transfers` and `AccountSignals`, the memo search index, and the `PaymentsNetwork` property graph |
| `sql/demo_queries.sql` | The demo queries in video order, with expected results |
| `data/*.sql` | Generated INSERT statements, including the embeddings |
| `profile_pics/` | 40 AI-generated profile photos of fictional people |
| `assets/` | Contact sheets of the ring and of customers, for video overlays |
| `gemini-cli/` | Gemini CLI extension for the Spanner remote MCP server |
| `tools/dataset.py` | The single source of truth for people, transfers and signals |
| `tools/build_data.py` | Computes embeddings and regenerates `data/*.sql` |
| `tools/embeddings.json` | Cached embeddings, so rebuilding doesn't call the API |

`setup.sh` uploads `profile_pics/` to `gs://PHOTO_BUCKET/profile_pics/`, and
`People.ProfilePhotoUrl` links to those copies.

### Schema notes

* Spanner Studio captions graph nodes with their alphabetically first
  property, so every `People` column sorts after `Name`.
* `PersonId` is a readable handle such as `robin.locksley`, so keys look
  good in results and in the graph view.
* `Transfers` is interleaved in `People`: each transfer is stored with its
  sender. Graph algorithms such as PageRank require this layout. Because of
  it, the sender column is named after the parent key, `PersonId`, and the
  graph exposes it as the `SenderId` property.
* `Transfers.MemoTokens` is a `TOKENLIST` column with a search index, so GQL
  can call `SEARCH()` on memos. A graph only sees the columns that exist when
  it's created, so the schema creates the token column first.
* `People.PageRank` starts empty. Query 5a fills it with
  `EXPORT DATA ... format = 'CLOUD_SPANNER'`.
* `setup.sh` creates the `GeminiModel` remote model after the schema, because
  its endpoint includes your project ID.

## Change the data

Edit `tools/dataset.py`, then regenerate the SQL. Embeddings are recomputed
only for bios or photos that changed, using `gemini-embedding-2` on Vertex AI
(128 dimensions, one vector space for text and images):

```bash
pip install google-genai
python3 tools/build_data.py --project "${PROJECT_ID}"
RECREATE=1 ./setup.sh
```

## Clean up

An Enterprise instance bills by the hour. Delete the database when you're done
recording:

```bash
./teardown.sh                      # Deletes the database only.
DELETE_INSTANCE=1 ./teardown.sh    # Also deletes the instance (only if setup.sh created it).
```

Teardown keeps the photo bucket. To delete the photos too, run
`gcloud storage rm --recursive gs://PHOTO_BUCKET/profile_pics`.
