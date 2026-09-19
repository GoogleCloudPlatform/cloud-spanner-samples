# Spanner Secure Parameters Example: .NET

This directory contains a .NET example for Spanner Secure Parameters using Cloud Spanner and the PostgreSQL dialect.

The application automatically:
1.  Creates a temporary database.
2.  Creates the schema (table, secure view, role).
3.  Inserts sample data.
4.  Queries the view using different secure contexts.
5.  Cleans up (drops) the temporary database.

## Prerequisites

Follow the prerequisites in the [parent directory README](../README.md).

## Run the Example

Run the application using the dotnet CLI:

```bash
dotnet run
```
