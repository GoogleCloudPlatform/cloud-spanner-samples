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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.ByteArray;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.IntegrationTest;
import com.google.cloud.spanner.IntegrationTestEnv;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ReadOnlyTransaction;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Value;
import com.google.cloud.spanner.testing.RemoteSpannerHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(IntegrationTest.class)
public class FinAppIT {

  private SpannerDaoInterface spannerDao;
  private DatabaseClient databaseClient;
  private static Database db;

  @ClassRule public static IntegrationTestEnv env = new IntegrationTestEnv();

  @BeforeClass
  public static void setup() throws IOException {
    RemoteSpannerHelper testHelper = env.getTestHelper();
    db =
        testHelper.createTestDatabase(
            extractStatementsFromSDLFile("src/main/java/com/google/finapp/schema.sdl"));
  }

  @Before
  public void setupSpannerDao() {
    RemoteSpannerHelper testHelper = env.getTestHelper();
    databaseClient = testHelper.getDatabaseClient(db);
    if (System.getProperty("SPANNER_USE_JDBC") == null
        || System.getProperty("SPANNER_USE_JDBC").equalsIgnoreCase("false")) {
      spannerDao = new SpannerDaoImpl(databaseClient);
    } else {
      spannerDao =
          new SpannerDaoJDBCImpl(
              testHelper.getOptions().getProjectId(),
              testHelper.getInstanceId().getInstance(),
              db.getId().getDatabase());
    }
  }

  private static List<String> extractStatementsFromSDLFile(String filename)
      throws FileNotFoundException {
    File file = new File(filename);
    BufferedReader reader = new BufferedReader(new FileReader(file));
    StringBuilder builder = new StringBuilder();
    try (Scanner scanner = new Scanner(reader)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        if (!line.startsWith("--")) { // ignore comments
          builder.append(line);
        }
      }
    }
    return List.of(builder.toString().split(";")); // separate into individual statements
  }

  @AfterClass
  public static void tearDown() {
    db.drop();
  }

  @Test
  public void createAccount_createsSingleValidAccount() throws Exception {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal amount = new BigDecimal(2);
    spannerDao.createAccount(
        accountId,
        AccountType.UNSPECIFIED_ACCOUNT_TYPE /* = 0*/,
        AccountStatus.UNSPECIFIED_ACCOUNT_STATUS /* = 0*/,
        amount);
    try (ResultSet resultSet =
        databaseClient
            .singleUse()
            .read(
                "Account",
                KeySet.singleKey(Key.of(accountId)),
                Arrays.asList("AccountType", "AccountStatus", "Balance"))) {
      int count = 0;
      while (resultSet.next()) {
        assertThat(resultSet.getLong(0)).isEqualTo(0);
        assertThat(resultSet.getLong(1)).isEqualTo(0);
        assertThat(resultSet.getBigDecimal(2)).isEqualTo(amount);
        count++;
      }
      assertThat(count).isEqualTo(1);
    }
  }

  @Test
  public void moveAccountBalance_validTransfer() throws Exception {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(10);
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(fromAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(fromAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build(),
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(toAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(toAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));
    ImmutableMap result = spannerDao.moveAccountBalance(fromAccountId, toAccountId, amount);
    try (ResultSet resultSet =
        databaseClient
            .singleUse()
            .read(
                "Account",
                KeySet.newBuilder()
                    .addKey(Key.of(fromAccountId))
                    .addKey(Key.of(toAccountId))
                    .build(),
                Arrays.asList("AccountId", "Balance"))) {
      int count = 0;
      while (resultSet.next()) {
        if (resultSet.getBytes(0).equals(fromAccountId)) {
          assertThat(resultSet.getBigDecimal(1)).isEqualTo(fromAccountBalance.subtract(amount));
          count++;
        } else if (resultSet.getBytes(0).equals(toAccountId)) {
          assertThat(resultSet.getBigDecimal(1)).isEqualTo(toAccountBalance.add(amount));
          count++;
        }
      }
      assertThat(count).isEqualTo(2);
      assertThat(result.keySet()).containsExactly(fromAccountId, toAccountId);
      assertThat(result.get(fromAccountId)).isEqualTo(fromAccountBalance.subtract(amount));
      assertThat(result.get(toAccountId)).isEqualTo(toAccountBalance.add(amount));
    }
  }

  @Test
  public void moveAccountBalance_negativeAmount_throwsException() throws Exception {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(-10);
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(fromAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(fromAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build(),
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(toAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(toAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));

    assertThrows(
        IllegalArgumentException.class,
        () -> spannerDao.moveAccountBalance(fromAccountId, toAccountId, amount));
  }

  @Test
  public void moveAccountBalance_tooLargeAmount_throwsException() throws Exception {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(25);
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(fromAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(fromAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build(),
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(toAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(toAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));

    assertThrows(
        IllegalArgumentException.class,
        () -> spannerDao.moveAccountBalance(fromAccountId, toAccountId, amount));
  }

  @Test
  public void createTransactionForAccount_isCredit_subtractsFromAccountBalance() throws Exception {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(60);
    BigDecimal amount = new BigDecimal(10);
    boolean isCredit = true;
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(accountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(oldAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));
    BigDecimal outputNewBalance =
        spannerDao.createTransactionForAccount(accountId, amount, isCredit);
    assertThat(outputNewBalance).isEqualTo(oldAccountBalance.subtract(amount));
    try (ReadOnlyTransaction transaction = databaseClient.readOnlyTransaction();
        ResultSet transactionResultSet =
            transaction.read(
                "TransactionHistory",
                KeySet.all(),
                Arrays.asList("Amount", "IsCredit", "AccountId"));
        ResultSet accountResultSet =
            transaction.read(
                "Account", KeySet.singleKey(Key.of(accountId)), Arrays.asList("Balance")); ) {
      int count = 0;
      boolean transactionSeen = false;
      boolean accountSeen = false;
      while (transactionResultSet.next()) {
        if (transactionResultSet.getBytes(2).equals(accountId)) {
          assertThat(transactionResultSet.getBigDecimal(0)).isEqualTo(amount);
          assertThat(transactionResultSet.getBoolean(1)).isEqualTo(isCredit);
          count++;
          transactionSeen = true;
        }
      }
      while (accountResultSet.next()) {
        assertThat(accountResultSet.getBigDecimal(0)).isEqualTo(oldAccountBalance.subtract(amount));
        count++;
        accountSeen = true;
      }
      assertThat(count).isEqualTo(2);
      assertThat(transactionSeen).isTrue();
      assertThat(accountSeen).isTrue();
    }
  }

  @Test
  public void createTransactionForAccount_notIsCredit_addsToAccountBalance() throws Exception {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(75);
    BigDecimal amount = new BigDecimal(10);
    boolean isCredit = false;
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(accountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(oldAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));
    BigDecimal outputNewBalance =
        spannerDao.createTransactionForAccount(accountId, amount, isCredit);
    assertThat(outputNewBalance).isEqualTo(oldAccountBalance.add(amount));
    try (ReadOnlyTransaction transaction = databaseClient.readOnlyTransaction();
        ResultSet transactionResultSet =
            transaction.read(
                "TransactionHistory",
                KeySet.all(),
                Arrays.asList("Amount", "IsCredit", "AccountId"));
        ResultSet accountResultSet =
            transaction.read(
                "Account", KeySet.singleKey(Key.of(accountId)), Arrays.asList("Balance")); ) {
      int count = 0;
      boolean transactionSeen = false;
      boolean accountSeen = false;
      while (transactionResultSet.next()) {
        if (transactionResultSet.getBytes(2).equals(accountId)) {
          assertThat(transactionResultSet.getBigDecimal(0)).isEqualTo(amount);
          assertThat(transactionResultSet.getBoolean(1)).isEqualTo(isCredit);
          count++;
          transactionSeen = true;
        }
      }
      while (accountResultSet.next()) {
        assertThat(accountResultSet.getBigDecimal(0)).isEqualTo(oldAccountBalance.add(amount));
        count++;
        accountSeen = true;
      }
      assertThat(count).isEqualTo(2);
      assertThat(transactionSeen).isTrue();
      assertThat(accountSeen).isTrue();
    }
  }

  @Test
  public void createTransactionForAccount_negativeAmount_throwsException() throws Exception {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(20);
    BigDecimal amount = new BigDecimal(-10);
    boolean isCredit = false;
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(accountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(oldAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));
    assertThrows(
        IllegalArgumentException.class,
        () -> spannerDao.createTransactionForAccount(accountId, amount, isCredit));
  }

  @Test
  public void createTransactionForAccount_tooLargeAmount_throwsException() throws Exception {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(20);
    BigDecimal amount = new BigDecimal(30);
    boolean isCredit = true;
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(accountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(oldAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));
    assertThrows(
        IllegalArgumentException.class,
        () -> spannerDao.createTransactionForAccount(accountId, amount, isCredit));
  }

  @Test
  public void getRecentTransactionsForAccount_valid() throws Exception {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(10);
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(fromAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(fromAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build(),
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(toAccountId)
                .set("AccountType")
                .to(AccountType.UNSPECIFIED_ACCOUNT_TYPE.getNumber())
                .set("AccountStatus")
                .to(AccountStatus.UNSPECIFIED_ACCOUNT_STATUS.getNumber())
                .set("Balance")
                .to(toAccountBalance)
                .set("CreationTimestamp")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));
    ImmutableMap result = spannerDao.moveAccountBalance(fromAccountId, toAccountId, amount);
    ImmutableList history =
        spannerDao.getRecentTransactionsForAccount(
            fromAccountId, Timestamp.MIN_VALUE, Timestamp.MAX_VALUE);
    assertThat(history).hasSize(1);
    assertThat(history.get(0)).isInstanceOf(TransactionEntry.class);
  }
}
