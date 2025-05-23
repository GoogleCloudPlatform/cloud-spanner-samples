/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package com.google.codelabs;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.SpannerExceptionFactory;
import com.google.cloud.spanner.SpannerOptions;
import com.google.spanner.admin.database.v1.CreateDatabaseMetadata;

public class App {

  // Create the Spanner database and schema
  public static void create(DatabaseAdminClient dbAdminClient, DatabaseId db,
      String location, String model) {
    System.out.println("Creating Spanner database...");
    List<String> statements = Arrays.asList(
      "CREATE TABLE Customers (\n"
          + "  CustomerId INT64 NOT NULL,\n"
          + "  FirstName STRING(256) NOT NULL,\n"
          + "  LastName STRING(256) NOT NULL,\n"
          + "  FullName STRING(512) AS (FirstName || ' ' || LastName) STORED,\n"
          + "  Email STRING(512) NOT NULL,\n"
          + "  EmailTokens TOKENLIST AS\n"
          + "    (TOKENIZE_SUBSTRING(Email, ngram_size_min=>2, ngram_size_max=>3,\n"
          + "      relative_search_types=>[\"all\"])) HIDDEN,\n"
          + "  Address STRING(MAX)\n"
          + ") PRIMARY KEY (CustomerId)",

      "CREATE INDEX CustomersByEmail\n"
          + "ON Customers(Email)",

      "CREATE SEARCH INDEX CustomersFuzzyEmail\n"
          + "ON Customers(EmailTokens)",

      "CREATE TABLE Accounts (\n"
          + "  AccountId INT64 NOT NULL,\n"
          + "  CustomerId INT64 NOT NULL,\n"
          + "  AccountType STRING(256) NOT NULL,\n"
          + "  Balance NUMERIC NOT NULL,\n"
          + "  OpenDate TIMESTAMP NOT NULL\n"
          + ") PRIMARY KEY (AccountId)",

      "CREATE INDEX AccountsByCustomer\n"
          + "ON Accounts (CustomerId)",

      "CREATE TABLE TransactionLedger (\n"
          + "  TransactionId INT64 NOT NULL,\n"
          + "  AccountId INT64 NOT NULL,\n"
          + "  TransactionType STRING(256) NOT NULL,\n"
          + "  Amount NUMERIC NOT NULL,\n"
          + "  Timestamp TIMESTAMP NOT NULL"
          + "  OPTIONS(allow_commit_timestamp=true),\n"
          + "  Category STRING(256),\n"
          + "  Description STRING(MAX),\n"
          + "  CategoryTokens TOKENLIST AS (TOKENIZE_FULLTEXT(Category)) HIDDEN,\n"
          + "  DescriptionTokens TOKENLIST AS (TOKENIZE_FULLTEXT(Description)) HIDDEN\n"
          + ") PRIMARY KEY (AccountId, TransactionId),\n"
          + "INTERLEAVE IN PARENT Accounts ON DELETE CASCADE",

      "CREATE INDEX TransactionLedgerByAccountType\n"
          + "ON TransactionLedger(AccountId, TransactionType)",

      "CREATE INDEX TransactionLedgerByCategory\n"
          + "ON TransactionLedger(AccountId, Category)",

      "CREATE SEARCH INDEX TransactionLedgerTextSearch\n"
          + "ON TransactionLedger(CategoryTokens, DescriptionTokens)",

      "CREATE MODEL TransactionCategoryModel\n"
          + "INPUT (prompt STRING(MAX))\n"
          + "OUTPUT (content STRING(MAX))\n"
          + "REMOTE OPTIONS (\n"
          + "  endpoint = '//aiplatform.googleapis.com/projects/" + db.getInstanceId().getProject()
              + "/locations/" + location + "/publishers/google/models/" + model + "',\n"
          + "  default_batch_size = 1\n"
          + ")");
    OperationFuture<Database, CreateDatabaseMetadata> op = dbAdminClient.createDatabase(
        db.getInstanceId().getInstance(),
        db.getDatabase(),
        statements);
    try {
      Database dbOperation = op.get();
      System.out.println("Created Spanner database [" + dbOperation.getId() + "]");
    } catch (ExecutionException e) {
      throw (SpannerException) e.getCause();
    } catch (InterruptedException e) {
      throw SpannerExceptionFactory.propagateInterrupt(e);
    }
  }

  static void printUsageAndExit() {
    System.out.println("Online Online Banking Application 1.0.0");
    System.out.println("Usage:");
    System.out.println("  java -jar target/onlinebanking.jar <command> [command_option(s)]");
    System.out.println("");
    System.out.println("Examples:");
    System.out.println("  java -jar target/onlinebanking.jar create");
    System.out.println("      - Create a sample Cloud Spanner database and schema in your "
        + "project.\n");
    System.exit(1);
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      printUsageAndExit();
    }

    String instanceId = System.getProperty("SPANNER_INSTANCE", System.getenv("SPANNER_INSTANCE"));
    String databaseId = System.getProperty("SPANNER_DATABASE", System.getenv("SPANNER_DATABASE"));
    String location = System.getenv().getOrDefault("SPANNER_LOCATION", "us-central1");
    String model = System.getenv().getOrDefault("SPANNER_MODEL", "gemini-2.0-flash-lite");
    if (instanceId == null || databaseId == null) {
      System.err.println("Missing one or more required environment variables: SPANNER_INSTANCE or "
          + "SPANNER_DATABASE");
      System.exit(1);
    }

    BigQueryOptions bigqueryOptions = BigQueryOptions.newBuilder().build();
    BigQuery bigquery = bigqueryOptions.getService();

    SpannerOptions spannerOptions = SpannerOptions.newBuilder().build();
    try (Spanner spanner = spannerOptions.getService()) {
      String command = args[0];
      DatabaseId db = DatabaseId.of(spannerOptions.getProjectId(), instanceId, databaseId);
      DatabaseClient dbClient = spanner.getDatabaseClient(db);
      DatabaseAdminClient dbAdminClient = spanner.getDatabaseAdminClient();

      switch (command) {
        case "create":
          create(dbAdminClient, db, location, model);
          break;
        default:
          printUsageAndExit();
      }
    }
  }
}
