# Cymbal Bank fraud demo

Answer questions about Cymbal Bank payments with the `spanner` MCP server.

- Database:
  `projects/@PROJECT_ID@/instances/@SPANNER_INSTANCE@/databases/@SPANNER_DATABASE@`
- Call `get_database_ddl` first, so you know the tables and the
  `PaymentsNetwork` property graph.
- Prefer GQL on the graph: `GRAPH PaymentsNetwork MATCH ...`.
- Only run read-only queries, with `execute_sql_readonly`.
- Show the query you ran, then the results as a table.
