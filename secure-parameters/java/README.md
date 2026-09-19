# Spanner Secure Parameters Example: Java

This directory contains a Java example for Spanner Secure Parameters using Cloud Spanner and the GoogleSQL dialect.

The application automatically:
1.  Creates a temporary database.
2.  Creates the schema (table, secure view, role).
3.  Inserts sample data.
4.  Queries the view using different secure contexts.
5.  Cleans up (drops) the temporary database.

## Prerequisites

Follow the prerequisites in the [parent directory README](../README.md).

## Run the Example

Execute the Java application using Maven:

```bash
mvn clean compile exec:java
```
