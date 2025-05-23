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

import java.io.FileReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.connection.v1.ConnectionName;
import com.google.cloud.bigquery.JobException;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.ReadOnlyTransaction;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.SpannerExceptionFactory;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.TimestampBound;
import com.google.spanner.admin.database.v1.CreateDatabaseMetadata;
import com.opencsv.CSVReader;

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

  // Insert customers from CSV
  public static void insertCustomers(DatabaseClient dbClient) {
    System.out.println("Inserting customers...");
    dbClient
        .readWriteTransaction()
        .run(transaction -> {
          int count = 0;
          List<Statement> statements = new ArrayList<>();
          try (CSVReader reader = new CSVReader(new FileReader("data/customers.csv"))) {
            reader.skip(1);
            String[] line;
            while ((line = reader.readNext()) != null) {
              Statement statement = Statement.newBuilder(
                  "INSERT INTO Customers (CustomerId, FirstName, LastName, Email, Address) "
                      + "VALUES (@customerId, @firstName, @lastName, @email, @address)")
                  .bind("customerId").to(Long.parseLong(line[0]))
                  .bind("firstName").to(line[1])
                  .bind("lastName").to(line[2])
                  .bind("email").to(line[3])
                  .bind("address").to(line[4])
                  .build();
              statements.add(statement);
              count++;
            }
            transaction.batchUpdate(statements);
            System.out.println("Inserted " + count + " customers");
            return null;
          }
        });
  }

  // Insert accounts from CSV
  public static void insertAccounts(DatabaseClient dbClient) {
    System.out.println("Inserting accounts...");
    dbClient
        .readWriteTransaction()
        .run(transaction -> {
          int count = 0;
          List<Statement> statements = new ArrayList<>();
          try (CSVReader reader = new CSVReader(new FileReader("data/accounts.csv"))) {
            reader.skip(1);
            String[] line;
            while ((line = reader.readNext()) != null) {
              Statement statement = Statement.newBuilder(
                "INSERT INTO Accounts (AccountId, CustomerId, AccountType, Balance, OpenDate) "
                    + "VALUES (@accountId, @customerId, @accountType, @balance, @openDate)")
                .bind("accountId").to(Long.parseLong(line[0]))
                .bind("customerId").to(Long.parseLong(line[1]))
                .bind("accountType").to(line[2])
                .bind("balance").to(new BigDecimal(line[3]))
                .bind("openDate").to(line[4])
                .build();
              statements.add(statement);
              count++;
            }
            transaction.batchUpdate(statements);
            System.out.println("Inserted " + count + " accounts");
            return null;
          }
        });
  }

  // Insert transactions from CSV
  public static void insertTransactions(DatabaseClient dbClient) {
    System.out.println("Inserting transactions...");
    dbClient
        .readWriteTransaction()
        .run(transaction -> {
          int count = 0;
          List<Statement> statements = new ArrayList<>();
          try (CSVReader reader = new CSVReader(new FileReader("data/transactions.csv"))) {
            reader.skip(1);
            String[] line;

            // Specify timestamps that are within last 30 days
            Random random = new Random();
            Instant startTime = Instant.now().minus(15, ChronoUnit.DAYS);
            Instant currentTimestamp = startTime;

            Map<Long, BigDecimal> balanceChanges = new HashMap<>();
            while ((line = reader.readNext()) != null) {
              long accountId = Long.parseLong(line[1]);
              String transactionType = line[2];
              BigDecimal amount = new BigDecimal(line[3]);
              int randomMinutes = random.nextInt(60) + 1;
              currentTimestamp = currentTimestamp.plus(Duration.ofMinutes(randomMinutes));
              Timestamp timestamp = Timestamp.ofTimeSecondsAndNanos(
                  currentTimestamp.getEpochSecond(), currentTimestamp.getNano());
              Statement statement = Statement.newBuilder(
                "INSERT INTO TransactionLedger (TransactionId, AccountId, TransactionType, Amount,"
                    + "Timestamp, Category, Description) "
                    + "VALUES (@transactionId, @accountId, @transactionType, @amount, @timestamp,"
                    + "@category, @description)")
                .bind("transactionId").to(Long.parseLong(line[0]))
                .bind("accountId").to(accountId)
                .bind("transactionType").to(transactionType)
                .bind("amount").to(amount)
                .bind("timestamp").to(timestamp)
                .bind("category").to(line[5])
                .bind("description").to(line[6])
                .build();
              statements.add(statement);

              // Track balance changes per account
              BigDecimal balanceChange = balanceChanges.getOrDefault(accountId,
                  BigDecimal.ZERO);
              if ("Credit".equalsIgnoreCase(transactionType)) {
                balanceChanges.put(accountId, balanceChange.add(amount));
              } else if ("Debit".equalsIgnoreCase(transactionType)) {
                balanceChanges.put(accountId, balanceChange.subtract(amount));
              } else {
                System.err.println("Unsupported transaction type: " + transactionType);
                continue;
              }

              count++;
            }

            // Apply final balance updates
            for (Map.Entry<Long, BigDecimal> entry : balanceChanges.entrySet()) {
              long accountId = entry.getKey();
              BigDecimal balanceChange = entry.getValue();

              Struct row = transaction.readRow(
                  "Accounts",
                  Key.of(accountId),
                  List.of("Balance"));
              if (row != null) {
                BigDecimal currentBalance = row.getBigDecimal("Balance");
                BigDecimal updatedBalance = currentBalance.add(balanceChange);
                Statement statement = Statement.newBuilder(
                  "UPDATE Accounts SET Balance = @balance WHERE AccountId = @accountId")
                  .bind("accountId").to(accountId)
                  .bind("balance").to(updatedBalance)
                  .build();
                statements.add(statement);
              }
            }

            transaction.batchUpdate(statements);
            System.out.println("Inserted " + count + " transactions");
          }
          return null;
        });
  }

  // Use Vertex AI to set the category of transactions
  public static void categorize(DatabaseClient dbClient) {
    System.out.println("Categorizing transactions...");
    try {
      // Create a prompt to instruct the LLM how to categorize the transactions
      String categories = String.join(", ", Arrays.asList("Entertainment", "Gifts", "Groceries",
          "Investment", "Medical", "Movies", "Online Shopping", "Other", "Purchases", "Refund",
          "Restaurants", "Salary", "Transfer", "Transportation", "Utilities"));
      String prompt = "Categorize the following financial activity into one of these "
          + "categories: " +  categories + ". Return Other if the description cannot be mapped to "
          + "one of these categories.  Only return the exact category string, no other text or "
          + "punctuation or reasoning. Description: ";
      String sql = "UPDATE TransactionLedger SET Category = (\n"
          + "  SELECT content FROM ML.PREDICT(MODEL `TransactionCategoryModel`, (\n"
          + "    SELECT CONCAT('" + prompt + "', CASE WHEN TRIM(Description) = ''\n"
          + "    THEN 'Other' ELSE Description END) AS prompt\n"
          + "  ))\n"
          + ") WHERE TRUE";

      // Use partitioned update to batch update a large number of rows
      dbClient.executePartitionedUpdate(Statement.of(sql));
      System.out.println("Completed categorizing transactions");
    } catch (SpannerException e) {
      throw e;
    }
  }

  // Get current account balance(s) by customer
  public static void getBalance(DatabaseClient dbClient, long customerId) {
    String query = "SELECT AccountId, Balance\n"
        + "FROM Accounts\n"
        + "WHERE CustomerId = @customerId";
    Statement statement = Statement.newBuilder(query)
        .bind("customerId").to(customerId)
        .build();

    // Ignore ongoing transactions, use stale reads as seconds-old data is sufficient
    TimestampBound stalenessBound = TimestampBound.ofMaxStaleness(5, TimeUnit.SECONDS);
    try (ReadOnlyTransaction transaction = dbClient.singleUseReadOnlyTransaction(stalenessBound);
        ResultSet resultSet = transaction.executeQuery(statement);) {
      System.out.println("Account balances for customer " + customerId + ":");
      while (resultSet.next()) {
        System.out.println("  Account " + resultSet.getLong("AccountId") + ": "
            + resultSet.getBigDecimal("Balance"));
      }
    }
  }

  // Find customers by email
  public static void findCustomers(DatabaseClient dbClient, String email) {
    // Query using fuzzy search (ngrams) to allow for spelling mistakes
    String query = "SELECT CustomerId, Email\n"
        + "FROM Customers\n"
        + "WHERE SEARCH_NGRAMS(EmailTokens, @email)\n"
        + "ORDER BY SCORE_NGRAMS(EmailTokens, @email) DESC\n"
        + "LIMIT 10";
    Statement statement = Statement.newBuilder(query)
        .bind("email").to(email)
        .build();

    try (ReadOnlyTransaction transaction = dbClient.singleUseReadOnlyTransaction();
        ResultSet resultSet = transaction.executeQuery(statement)) {
      System.out.println("Customer emails matching " + email + " (top 10 matches):");
      while (resultSet.next()) {
        System.out.println("  Customer " + resultSet.getLong("CustomerId") + ": "
            + resultSet.getString("Email"));
      }
    }
  }

  // Get total monthly spending for a customer by category
  public static void getSpending(DatabaseClient dbClient, long customerId, String category) {
    // Query category using full-text search
    String query = "SELECT SUM(Amount) as TotalSpending\n"
        + "FROM TransactionLedger t\n"
        + "JOIN Accounts a\n"
        + "  ON t.AccountId = a.AccountId\n"
        + "WHERE t.TransactionType = 'Debit'\n"
        + "  AND a.CustomerId = @customerId\n"
        + "  AND t.Timestamp >= TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL -30 DAY)\n"
        + "  AND (SEARCH(t.CategoryTokens, @category) OR SEARCH(t.DescriptionTokens, @category))";
    Statement statement = Statement.newBuilder(query)
        .bind("customerId").to(customerId)
        .bind("category").to(category)
        .build();

    try (ReadOnlyTransaction transaction = dbClient.singleUseReadOnlyTransaction();
        ResultSet resultSet = transaction.executeQuery(statement);) {
      System.out.println("Total spending for customer " + customerId + " under category "
          + category + ":");
      while (resultSet.next()) {
        BigDecimal totalSpending = BigDecimal.ZERO;
        if (!resultSet.isNull("TotalSpending")) {
          totalSpending = resultSet.getBigDecimal("TotalSpending");
        }
        System.out.println("  " + totalSpending);
      }
    }
  }

  // Get customers for quarterly marketing campaign in BigQuery using Spanner data
  public static void campaign(BigQuery bq, DatabaseId db, String location, String campaignId,
      int threshold) {
    // The BigQuery dataset, table, and Spanner connection must already exist for this to succeed
    ConnectionName connection = ConnectionName.of(db.getInstanceId().getProject(), location,
        "spanner-connection");

    // Use a federated query to bring Spanner data into BigQuery
    String bqQuery = "SELECT cs.CampaignName, c.CustomerId, c.FullName, t.TotalSpending\n"
        + "FROM MarketingCampaigns.CustomerSegments cs\n"
        + "JOIN EXTERNAL_QUERY('" + connection.toString() + "',\n"
        + "  \"SELECT t.AccountId, SUM(t.Amount) AS TotalSpending"
        + "   FROM TransactionLedger t"
        + "   WHERE t.Timestamp >= TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL -90 DAY)"
        + "   GROUP BY t.AccountId"
        + "   HAVING SUM(t.Amount) > " + threshold + "\"\n"
        + ") t ON cs.CustomerId = t.AccountId\n"
        + "JOIN EXTERNAL_QUERY('" + connection.toString() + "',\n"
        + "  \"SELECT CustomerId, FullName"
        + "   FROM Customers\"\n"
        + ") c ON c.CustomerId = cs.CustomerId\n"
        + "WHERE cs.CampaignId = '" + campaignId + "'";
    try {
      QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(bqQuery).build();
      TableResult results = bq.query(queryConfig);

      System.out.println("Customers for campaign (" + campaignId + "):");
      results.iterateAll().forEach(row -> {
        System.out.println("  " + row.get("FullName").getStringValue()
            + " (" + row.get("CustomerId").getStringValue() + ")");
      });
    } catch (JobException e) {
      throw (BigQueryException) e.getCause();
    } catch (InterruptedException e) {
      throw SpannerExceptionFactory.propagateInterrupt(e);
    }
  }

  // Delete the Spanner database
  public static void delete(DatabaseAdminClient dbAdminClient, DatabaseId db) {
    System.out.println("Deleting Spanner database...");
    try {
      dbAdminClient.dropDatabase(
          db.getInstanceId().getInstance(),
          db.getDatabase());
      System.out.println("Deleted database [" + db.getName() + "]");
    } catch (SpannerException e) {
      throw e;
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
    System.out.println("  java -jar target/onlinebanking.jar insert");
    System.out.println("      - Insert sample Customers, Accounts, and Transactions into the "
        + "database.\n");
    System.out.println("  java -jar target/onlinebanking.jar categorize");
    System.out.println("      - Use AI to categorize transactions in the database.\n");
    System.out.println("  java -jar target/onlinebanking.jar query balance 1");
    System.out.println("      - Query customer account balance(s) by customer id.\n");
    System.out.println("  java -jar target/onlinebanking.jar query email madi");
    System.out.println("      - Find customers by email using fuzzy search.\n");
    System.out.println("  java -jar target/onlinebanking.jar query spending 1 groceries");
    System.out.println("      - Query customer spending by customer id and category using "
        + "full-text search.\n");
    System.out.println("  java -jar target/onlinebanking.jar campaign campaign1 5000");
    System.out.println("      - Use Federated Queries (BigQuery) to find customers that match a "
        + "marketing campaign by name based on a recent spending threshold.\n");
    System.out.println("  java -jar target/onlinebanking.jar delete");
    System.out.println("      - Delete the sample Cloud Spanner database.");
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
        case "insert":
          String insertType = (args.length >= 2) ? args[1] : "";
          if (insertType.equals("customers")) {
            insertCustomers(dbClient);
          } else if (insertType.equals("accounts")) {
            insertAccounts(dbClient);
          } else if (insertType.equals("transactions")) {
            insertTransactions(dbClient);
          } else {
            insertCustomers(dbClient);
            insertAccounts(dbClient);
            insertTransactions(dbClient);
          }
          break;
        case "categorize":
          categorize(dbClient);
          break;
        case "query":
          String queryType = (args.length >= 2) ? args[1] : "";
          if (queryType.equals("balance")) {
            long customerId = (args.length >= 3) ? Long.parseLong(args[2]) : 1L;
            getBalance(dbClient, customerId);
          } else if (queryType.equals("email")) {
            String email = (args.length >= 3) ? args[2] : "";
            findCustomers(dbClient, email);
          } else if (queryType.equals("spending")) {
            long customerId = (args.length >= 3) ? Long.parseLong(args[2]) : 1L;
            String category = (args.length >= 4) ? args[3] : "";
            getSpending(dbClient, customerId, category);
          } else {
            printUsageAndExit();
          }
          break;
        case "campaign":
          String campaignId = (args.length >= 2) ? args[1] : "";
          int threshold = (args.length >= 3) ? Integer.parseInt(args[2]) : 5000;
          campaign(bigquery, db, location, campaignId, threshold);
          break;
        case "delete":
          delete(dbAdminClient, db);
          break;
        default:
          printUsageAndExit();
      }
    }
  }
}
