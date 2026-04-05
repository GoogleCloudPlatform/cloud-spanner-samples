# Spanner IAM Graph

**By Moshe Youdkovich** (Data Solutions Architect) **and Shimon Ben Ishay** (Cloud Solutions Architect), **Google Cloud**

---

📖 **Read the full article on Medium:** [Securing Identity Sprawl: Building a Real-Time Access Graph with Google Cloud Spanner](https://medium.com/google-cloud/securing-identity-sprawl-building-a-real-time-access-graph-with-google-cloud-spanner-2651991d3c3f)

Real-time access graph for GCP using Cloud Spanner's native property graph support.

Ingests Cloud Identity group membership changes and IAM policy bindings via Pub/Sub, writes them to Spanner, and exposes them for graph queries using GQL.

## Why

IAM policy bindings don't resolve nested group memberships. A service account can have access to production resources through 4 levels of group nesting and IAM will show zero bindings for it. Policy Analyzer can resolve this per-principal, but you have to know who to ask about. This pipeline finds all risky paths automatically and in real time.

## How it works

```
Cloud Identity logs ─┐
                      ├─> Pub/Sub ─> Cloud Run ─> Spanner (Property Graph)
SetIamPolicy logs ───┘
```

1. A logging sink captures `ADD_GROUP_MEMBER` and `SetIamPolicy` events
2. Events flow to a Pub/Sub topic
3. Cloud Run processes them and writes nodes/edges to Spanner
4. GQL queries traverse the graph to find transitive access paths

## Setup

1. Enable Cloud Identity log sharing in the Admin Console (Account > Legal and compliance > Sharing Options)
2. Edit `setup.sh` with your project ID and region
3. Run `./setup.sh`

## Graph queries

See `queries.sql` for examples. The key one:

```sql
GRAPH SecurityGraph
MATCH (identity:Identities)-[:IS_MEMBER]->(g:UserGroups)
      -[:NESTED_IN]->{1, 5}(parent:UserGroups)
      -[:HAS_PERMISSION]->(res:Resources {sensitivity: 'High'})
RETURN identity.email, g.name, parent.name, res.name;
```

This traverses up to 5 levels of group nesting and finds every identity that reaches a sensitive resource through nested groups.

## Files

- `main.py` - Cloud Run service that processes Pub/Sub events and writes to Spanner
- `schema.sql` - Spanner DDL for tables and property graph definition
- `queries.sql` - GQL queries for access path discovery, investigation, and audit
- `setup.sh` - Infrastructure setup script
- `Dockerfile` - Container definition for Cloud Run

## Requirements

- Spanner Enterprise edition (needed for property graph support)
- Cloud Identity log sharing enabled
- Cloud Run SA needs `roles/spanner.databaseUser`
