/*
 * Copyright 2020 Google Inc.
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

package com.google.finapp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.IntegrationTest;
import com.google.cloud.spanner.IntegrationTestEnv;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.testing.RemoteSpannerHelper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(IntegrationTest.class)
public class FinAppIT {

  private static SpannerDaoInterface JDBCDao;
  private static SpannerDaoInterface JavaDao;
  private static DatabaseClient databaseClient;
  @ClassRule
  public static IntegrationTestEnv env = new IntegrationTestEnv();
  private static Database db;

  @BeforeClass
  public static void setup() {
    final RemoteSpannerHelper testHelper = env.getTestHelper();
    db = testHelper.createTestDatabase(
        // taken directly from src/main/java/com/google/finapp/schema.sdl
        "CREATE TABLE Account (\n"
            + "  AccountId BYTES(16) NOT NULL,\n"
            + "  AccountType INT64 NOT NULL,\n"
            + "  CreationTimestamp TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),\n"
            + "  AccountStatus INT64 NOT NULL,\n"
            + "  Balance NUMERIC NOT NULL\n"
            + ") PRIMARY KEY (AccountId)\n",
        "CREATE TABLE TransactionHistory (\n"
            + "  AccountId BYTES(16) NOT NULL,\n"
            + "  EventTimestamp TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),\n"
            + "  IsCredit BOOL NOT NULL,\n"
            + "  Amount NUMERIC NOT NULL,\n"
            + "  Description STRING(MAX)\n"
            + ") PRIMARY KEY (AccountId, EventTimestamp DESC),\n"
            + "  INTERLEAVE IN PARENT Account ON DELETE CASCADE\n",
        "CREATE TABLE Customer (\n"
            + "  CustomerId BYTES(16) NOT NULL,\n"
            + "  Name STRING(MAX) NOT NULL,\n"
            + "  Address STRING(MAX) NOT NULL,\n"
            + ") PRIMARY KEY (CustomerId)\n",
        "CREATE TABLE CustomerRole (\n"
            + "  CustomerId BYTES(16) NOT NULL,\n"
            + "  RoleId BYTES(16) NOT NULL,\n"
            + "  Role STRING(MAX) NOT NULL,\n"
            + "  AccountId BYTES(16) NOT NULL,\n"
            + "  CONSTRAINT FK_AccountCustomerRole FOREIGN KEY (AccountId)\n"
            + "    REFERENCES Account(AccountId),\n"
            + ") PRIMARY KEY (CustomerId, RoleId),\n"
            + "  INTERLEAVE IN PARENT Customer ON DELETE CASCADE\n",
        "CREATE INDEX CustomerRoleByAccount ON CustomerRole(AccountId, CustomerId)\n",
        "CREATE TABLE Statement (\n"
            + "  AccountId BYTES(16) NOT NULL,\n"
            + "  StatementId BYTES(16) NOT NULL,\n"
            + "  Balance NUMERIC NOT NULL,\n"
            + "  StatementWindowStart TIMESTAMP NOT NULL,\n"
            + "  StatementWindowEnd TIMESTAMP NOT NULL,\n"
            + "  StatementSummary BYTES(MAX) NOT NULL\n"
            + ") PRIMARY KEY (AccountId, StatementId),\n"
            + "  INTERLEAVE IN PARENT Account ON DELETE CASCADE");
    final String databaseId = db.getId().getDatabase();
    final String projectId = testHelper.getOptions().getProjectId();
    final String instanceId = testHelper.getInstanceId().getInstance();
    JDBCDao = new SpannerDaoJDBCImpl(projectId, instanceId, databaseId);
    databaseClient = testHelper.getDatabaseClient(db);
    JavaDao = new SpannerDaoImpl(databaseClient);
    System.out.printf("New database \"%s\" created for project \"%s\" instance \"%s\"\n", databaseId, projectId,
        instanceId);
  }

  @AfterClass
  public static void tearDown() {
    db.drop();
  }

  @Test
  public void createAccountTest() throws SpannerDaoException {
    for (SpannerDaoInterface spannerDao : List.of(JavaDao, JDBCDao)) {
      ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
      spannerDao.createAccount(
          accountId,
          AccountType.UNSPECIFIED_ACCOUNT_TYPE /* = 0*/,
          AccountStatus.UNSPECIFIED_ACCOUNT_STATUS /* = 0*/,
          new BigDecimal(2));
      try (ResultSet resultSet =
          databaseClient
              .singleUse()
              .read(
                  "Account",
                  KeySet.newBuilder().addKey(Key.of(accountId)).build(),
                  Arrays.asList("AccountType", "AccountStatus", "Balance"))) {
        int count = 0;
        while (resultSet.next()) {
          assertEquals(0, resultSet.getLong(0));
          assertEquals(0, resultSet.getLong(1));
          assertEquals(new BigDecimal(2), resultSet.getBigDecimal(2));
          count++;
        }
        assertEquals(1, count);
      }
    }
  }
}
