package com.google.cloud.spanner.examples;

import com.google.cloud.spanner.*;
import com.google.spanner.v1.RequestOptions;
import com.google.protobuf.Value;
import java.util.Arrays;
import java.util.UUID;

public class App {
  public static void main(String[] args) {
    String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
    String instanceId = System.getenv("SPANNER_INSTANCE_ID");
    if (projectId == null || instanceId == null) {
      System.err.println("Error: GOOGLE_CLOUD_PROJECT and SPANNER_INSTANCE_ID environment variables must be set.");
      return;
    }
    String user = System.getenv("USER");
    if (user == null) user = "default";
    String databaseId = "psv-java-" + user + "-" + UUID.randomUUID().toString().substring(0, 8);

    SpannerOptions options = SpannerOptions.newBuilder().setProjectId(projectId).build();
    Spanner spanner = options.getService();

    try {
      DatabaseAdminClient adminClient = spanner.getDatabaseAdminClient();
      System.out.println("Setting up database " + databaseId + "...");
      try {
        adminClient.createDatabase(instanceId, databaseId, Arrays.asList(
            "CREATE TABLE UserData (UserID INT64 NOT NULL, UserName STRING(MAX), SecretData STRING(MAX)) PRIMARY KEY (UserID)",
            "CREATE VIEW MyUserData SQL SECURITY DEFINER AS SELECT UserData.UserID, UserData.UserName, UserData.SecretData FROM UserData WHERE UserData.UserID = CAST(SECURE_CONTEXT('user_id') AS INT64)",
            "CREATE ROLE psv_user",
            "GRANT SELECT ON VIEW MyUserData TO ROLE psv_user",
            "REVOKE SELECT ON TABLE UserData FROM ROLE psv_user"
        )).get();
        System.out.println("Database setup complete.");
      } catch (Exception e) {
        System.out.println("Failed to create database: " + e.getMessage());
        return;
      }

      DatabaseClient dbClient = spanner.getDatabaseClient(DatabaseId.of(options.getProjectId(), instanceId, databaseId));
      
      System.out.println("Inserting initial data...");
      dbClient.readWriteTransaction().run(
          new TransactionRunner.TransactionCallable<Void>() {
            @Override
            public Void run(TransactionContext transaction) throws Exception {
              transaction.executeUpdate(Statement.of("INSERT INTO UserData (UserID, UserName, SecretData) VALUES (1, 'Alice', 'Alice secret'), (2, 'Bob', 'Bob secret')"));
              return null;
            }
          });

      for (String userId : new String[]{"1", "2"}) {
        System.out.println("\nQuerying as user " + userId + "...");
        queryWithUserId(dbClient, userId);
      }
    } finally {
      try {
        System.out.println("Cleaning up database " + databaseId + "...");
        spanner.getDatabaseAdminClient().dropDatabase(instanceId, databaseId);
        System.out.println("Database cleaned up.");
      } catch (Exception e) {
        System.err.println("Failed to cleanup database: " + e.getMessage());
      }
      spanner.close();
    }
  }

  // [START spanner_query_with_secure_parameters]
  static void queryWithUserId(DatabaseClient dbClient, String userId) {
    RequestOptions.ClientContext clientContext =
        RequestOptions.ClientContext.newBuilder()
            .putSecureContext("user_id", Value.newBuilder().setStringValue(userId).build())
            .build();

    try (ResultSet resultSet =
        dbClient
            .singleUse()
            .executeQuery(
                Statement.of("SELECT * FROM MyUserData"), Options.clientContext(clientContext))) {
      int rowCount = 0;
      while (resultSet.next()) {
        long id = resultSet.getLong("UserID");
        if (!String.valueOf(id).equals(userId)) {
          throw new RuntimeException("Unexpected UserID " + id + " for user " + userId);
        }
        System.out.printf(
            "UserID: %d, UserName: %s, SecretData: %s%n",
            id, resultSet.getString("UserName"), resultSet.getString("SecretData"));
        rowCount++;
      }
      if (rowCount != 1) {
        throw new RuntimeException("Expected 1 row, got " + rowCount);
      }
    }
  }
  // [END spanner_query_with_secure_parameters]
}
