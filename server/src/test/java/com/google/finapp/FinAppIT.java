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
import com.google.cloud.spanner.testing.RemoteSpannerHelper;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Scanner;
import java.util.UUID;

import org.apache.http.util.ByteArrayBuffer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(IntegrationTest.class)
public class FinAppIT {
  private static Database db;
  private DatabaseClient databaseClient;
  private FinAppGrpc.FinAppBlockingStub finAppService;

  @ClassRule public static IntegrationTestEnv env = new IntegrationTestEnv();

  @ClassRule public static GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @BeforeClass
  public static void setup() throws IOException {
    RemoteSpannerHelper testHelper = env.getTestHelper();
    db = testHelper.createTestDatabase(extractStatementsFromSDLFile());
  }

  @Before
  public void setupGrpcServer() throws Exception {
    RemoteSpannerHelper testHelper = env.getTestHelper();
    databaseClient = testHelper.getDatabaseClient(db);
    SpannerDaoInterface spannerDao =
        (System.getProperty("SPANNER_USE_JDBC") == null
                || System.getProperty("SPANNER_USE_JDBC").equalsIgnoreCase("false"))
            ? new SpannerDaoImpl(databaseClient)
            : new SpannerDaoJDBCImpl(
                testHelper.getOptions().getProjectId(),
                testHelper.getInstanceId().getInstance(),
                db.getId().getDatabase());
    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();

    // Create a server, add service, start, and register for automatic graceful shutdown.
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new FinAppService(spannerDao))
            .build()
            .start());

    finAppService =
        FinAppGrpc.newBlockingStub(
            // Create a client channel and register for automatic graceful shutdown.
            grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()));
  }

  private static String[] extractStatementsFromSDLFile() throws FileNotFoundException {
    File file = new File("src/main/java/com/google/finapp/schema.sdl");
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
    return builder.toString().split(";"); // separate into individual statements
  }

  @AfterClass
  public static void tearDown() {
    db.drop();
  }

  @Test
  public void createCustomer_createsValidCustomer() {
    String name = "customer name";
    String address = "customer address";
    CreateCustomerResponse response =
        finAppService.createCustomer(
            CreateCustomerRequest.newBuilder().setName(name).setAddress(address).build());
    assertThat(response.getCustomerId().size()).isEqualTo(16);
    try (ResultSet resultSet =
        databaseClient
            .singleUse()
            .read(
                "Customer",
                KeySet.singleKey(
                    Key.of(ByteArray.copyFrom(response.getCustomerId().toByteArray()))),
                Arrays.asList("Name", "Address"))) {
      int count = 0;
      while (resultSet.next()) {
        assertThat(resultSet.getString(0)).isEqualTo(name);
        assertThat(resultSet.getString(1)).isEqualTo(address);
        count++;
      }
      assertThat(count).isEqualTo(1);
    }
  }

  @Test
  public void createAccount_createsValidAccount() {
    BigDecimal amount = new BigDecimal(2);
    CreateAccountResponse response =
        finAppService.createAccount(
            CreateAccountRequest.newBuilder()
                .setStatus(CreateAccountRequest.Status.UNSPECIFIED_ACCOUNT_STATUS /* = 0*/)
                .setBalance(amount.toString())
                .build());
    assertThat(response.getAccountId().size()).isEqualTo(16);
    try (ResultSet resultSet =
        databaseClient
            .singleUse()
            .read(
                "Account",
                KeySet.singleKey(Key.of(ByteArray.copyFrom(response.getAccountId().toByteArray()))),
                Arrays.asList("AccountStatus", "Balance"))) {
      int count = 0;
      while (resultSet.next()) {
        assertThat(resultSet.getLong("AccountStatus")).isEqualTo(0);
        assertThat(resultSet.getBigDecimal("Balance")).isEqualTo(amount);
        count++;
      }
      assertThat(count).isEqualTo(1);
    }
  }

  private void addAccountRow(
      ByteArray accountId, AccountStatus status, BigDecimal balance, Timestamp creationTimestamp) {
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Account")
                .set("AccountId")
                .to(accountId)
                .set("AccountStatus")
                .to(status.getNumber())
                .set("Balance")
                .to(balance)
                .set("CreationTimestamp")
                .to(creationTimestamp)
                .build()));
  }

  private void addActiveAccountRow(
      ByteArray accountId, BigDecimal balance, Timestamp creationTimestamp) {
    addAccountRow(accountId, AccountStatus.ACTIVE, balance, creationTimestamp);
  }

  private void addTestCustomer(ByteArray customerId, String name, String address) {
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("Customer")
                .set("CustomerId")
                .to(customerId)
                .set("Name")
                .to(name)
                .set("Address")
                .to(address)
                .build()));
  }

  @Test
  public void createCustomerRole_createsValidCustomerRole() {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray customerId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    String roleName = "role name";
    addActiveAccountRow(accountId, new BigDecimal(60), Timestamp.now());
    addTestCustomer(customerId, "customer name", "customer address");

    CreateCustomerRoleResponse response =
        finAppService.createCustomerRole(
            CreateCustomerRoleRequest.newBuilder()
                .setCustomerId(ByteString.copyFrom(customerId.toByteArray()))
                .setAccountId(ByteString.copyFrom(accountId.toByteArray()))
                .setName(roleName)
                .build());
    assertThat(response.getRoleId().size()).isEqualTo(16);
    try (ResultSet resultSet =
        databaseClient
            .singleUse()
            .read(
                "CustomerRole",
                KeySet.singleKey(
                    Key.of(customerId, ByteArray.copyFrom(response.getRoleId().toByteArray()))),
                Arrays.asList("Role", "AccountId"))) {
      int count = 0;
      while (resultSet.next()) {
        assertThat(resultSet.getString(0)).isEqualTo(roleName);
        assertThat(resultSet.getBytes(1)).isEqualTo(accountId);
        count++;
      }
      assertThat(count).isEqualTo(1);
    }
  }

  @Test
  public void moveAccountBalance_validTransfers() {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(44);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(1);
    addActiveAccountRow(fromAccountId, fromAccountBalance, Timestamp.now());
    addActiveAccountRow(toAccountId, toAccountBalance, Timestamp.now());
    MoveAccountBalanceResponse response =
        finAppService.moveAccountBalance(
            MoveAccountBalanceRequest.newBuilder()
                .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
                .setAmount(amount.toString())
                .build());

    assertThat(response.getFromAccountIdBalance()).isEqualTo("43");
    assertThat(response.getToAccountIdBalance()).isEqualTo("1");

    // Perform one more transfer to ensure the changes were persistent.
    response =
        finAppService.moveAccountBalance(
            MoveAccountBalanceRequest.newBuilder()
                .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
                .setAmount(amount.toString())
                .build());

    assertThat(response.getFromAccountIdBalance()).isEqualTo("42");
    assertThat(response.getToAccountIdBalance()).isEqualTo("2");
  }

  @Test
  public void moveAccountBalance_sameAccount_throwsException() throws Exception {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = ByteArray.copyFrom(fromAccountId.toByteArray());

    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.moveAccountBalance(
                    MoveAccountBalanceRequest.newBuilder()
                        .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                        .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
                        .setAmount("10")
                        .build()));
    assertThat(e.getMessage()).contains("\"To\" and \"from\" account IDs must be different");
  }


  @Test
  public void moveAccountBalance_negativeAmount_throwsException() throws Exception {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(-10);
    addActiveAccountRow(fromAccountId, fromAccountBalance, Timestamp.now());
    addActiveAccountRow(toAccountId, toAccountBalance, Timestamp.now());

    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.moveAccountBalance(
                    MoveAccountBalanceRequest.newBuilder()
                        .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                        .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
                        .setAmount(amount.toString())
                        .build()));
    assertThat(e.getMessage()).contains("Expected positive numeric value, found: -10");
  }

  @Test
  public void moveAccountBalance_tooLargeAmount_throwsException() {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(25);
    addActiveAccountRow(fromAccountId, fromAccountBalance, Timestamp.now());
    addActiveAccountRow(toAccountId, toAccountBalance, Timestamp.now());

    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.moveAccountBalance(
                    MoveAccountBalanceRequest.newBuilder()
                        .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                        .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
                        .setAmount(amount.toString())
                        .build()));
    assertThat(e.getMessage()).contains("Account balance cannot be negative");
  }

  @Test
  public void moveAccountBalance_missingAccount_throwsException() {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal amount = new BigDecimal(10);
    addActiveAccountRow(fromAccountId, fromAccountBalance, Timestamp.now());
    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.moveAccountBalance(
                    MoveAccountBalanceRequest.newBuilder()
                        .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                        .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
                        .setAmount(amount.toString())
                        .build()));
    assertThat(e.getMessage()).contains("Account not found");
  }

  @Test
  public void moveAccountBalance_frozenAccount_throwsException() {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(44);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(1);
    addActiveAccountRow(fromAccountId, fromAccountBalance, Timestamp.now());
    addAccountRow(toAccountId, AccountStatus.FROZEN, toAccountBalance, Timestamp.now());

    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.moveAccountBalance(
                    MoveAccountBalanceRequest.newBuilder()
                        .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                        .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
                        .setAmount(amount.toString())
                        .build()));
    assertThat(e.getMessage()).contains("Non-active accounts are not eligible for transfers");
  }

  @Test
  public void createTransactionForAccount_isCredit_subtractsFromAccountBalance() {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(60);
    BigDecimal amount = new BigDecimal(10);
    boolean isCredit = true;
    addActiveAccountRow(accountId, oldAccountBalance, Timestamp.now());

    CreateTransactionForAccountResponse response =
        finAppService.createTransactionForAccount(
            CreateTransactionForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(accountId.toByteArray()))
                .setAmount(amount.toString())
                .setIsCredit(isCredit)
                .build());

    assertThat(response.getNewBalance()).isEqualTo("50");
    try (ReadOnlyTransaction transaction = databaseClient.readOnlyTransaction();
        ResultSet transactionResultSet =
            transaction.read(
                "TransactionHistory",
                KeySet.all(),
                Arrays.asList("Amount", "IsCredit", "AccountId"));
        ResultSet accountResultSet =
            transaction.read(
                "Account", KeySet.singleKey(Key.of(accountId)), Arrays.asList("Balance"))) {
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
  public void createTransactionForAccount_notIsCredit_addsToAccountBalance() {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(75);
    BigDecimal amount = new BigDecimal(10);
    boolean isCredit = false;
    addActiveAccountRow(accountId, oldAccountBalance, Timestamp.now());

    CreateTransactionForAccountResponse response =
        finAppService.createTransactionForAccount(
            CreateTransactionForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(accountId.toByteArray()))
                .setAmount(amount.toString())
                .setIsCredit(isCredit)
                .build());

    assertThat(response.getNewBalance()).isEqualTo("85");
    try (ReadOnlyTransaction transaction = databaseClient.readOnlyTransaction();
        ResultSet transactionResultSet =
            transaction.read(
                "TransactionHistory",
                KeySet.all(),
                Arrays.asList("Amount", "IsCredit", "AccountId"));
        ResultSet accountResultSet =
            transaction.read(
                "Account", KeySet.singleKey(Key.of(accountId)), Arrays.asList("Balance"))) {
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
  public void createTransactionForAccount_negativeAmount_throwsException() {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(20);
    BigDecimal amount = new BigDecimal(-10);
    boolean isCredit = false;
    addActiveAccountRow(accountId, oldAccountBalance, Timestamp.now());

    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.createTransactionForAccount(
                    CreateTransactionForAccountRequest.newBuilder()
                        .setAccountId(ByteString.copyFrom(accountId.toByteArray()))
                        .setAmount(amount.toString())
                        .setIsCredit(isCredit)
                        .build()));
    assertThat(e.getMessage()).contains("Expected positive numeric value, found: -10");
  }

  @Test
  public void createTransactionForAccount_tooLargeAmount_throwsException() {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal oldAccountBalance = new BigDecimal(20);
    BigDecimal amount = new BigDecimal(30);
    boolean isCredit = true;
    addActiveAccountRow(accountId, oldAccountBalance, Timestamp.now());
    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.createTransactionForAccount(
                    CreateTransactionForAccountRequest.newBuilder()
                        .setAccountId(ByteString.copyFrom(accountId.toByteArray()))
                        .setAmount(amount.toString())
                        .setIsCredit(isCredit)
                        .build()));
    assertThat(e.getMessage()).contains("Account balance cannot be negative");
  }

  @Test
  public void getRecentTransactionsForAccount_validSingleTransaction() {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(10);
    addActiveAccountRow(fromAccountId, fromAccountBalance, Timestamp.now());
    addActiveAccountRow(toAccountId, toAccountBalance, Timestamp.now());
    finAppService.moveAccountBalance(
        MoveAccountBalanceRequest.newBuilder()
            .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
            .setAmount(amount.toString())
            .build());

    GetRecentTransactionsForAccountResponse response =
        finAppService.getRecentTransactionsForAccount(
            GetRecentTransactionsForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .setBeginTimestamp(Timestamp.MIN_VALUE.toProto())
                .setEndTimestamp(Timestamp.MAX_VALUE.toProto())
                .build());

    assertThat(response.getTransactionEntryList()).hasSize(1);
    TransactionEntry actual = response.getTransactionEntry(0);
    TransactionEntry expected_transaction =
        TransactionEntry.newBuilder()
            .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setEventTimestamp(actual.getEventTimestamp())
            .setIsCredit(true)
            .setAmount(amount.toString())
            .build();
    assertThat(response.getTransactionEntry(0)).isEqualTo(expected_transaction);
  }

  @Test
  public void getRecentTransactionsForAccount_validMultipleTransactions() {
    ByteArray fromAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray toAccountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal fromAccountBalance = new BigDecimal(20);
    BigDecimal toAccountBalance = new BigDecimal(0);
    BigDecimal amount = new BigDecimal(10);
    addActiveAccountRow(fromAccountId, fromAccountBalance, Timestamp.now());
    addActiveAccountRow(toAccountId, toAccountBalance, Timestamp.now());
    finAppService.moveAccountBalance(
        MoveAccountBalanceRequest.newBuilder()
            .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
            .setAmount(amount.toString())
            .build());
    finAppService.moveAccountBalance(
        MoveAccountBalanceRequest.newBuilder()
            .setFromAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
            .setToAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setAmount(amount.toString())
            .build());
    finAppService.moveAccountBalance(
        MoveAccountBalanceRequest.newBuilder()
            .setFromAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setToAccountId(ByteString.copyFrom(toAccountId.toByteArray()))
            .setAmount(amount.toString())
            .build());
    GetRecentTransactionsForAccountResponse response =
        finAppService.getRecentTransactionsForAccount(
            GetRecentTransactionsForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .setBeginTimestamp(Timestamp.MIN_VALUE.toProto())
                .setEndTimestamp(Timestamp.MAX_VALUE.toProto())
                .build());
    assertThat(response.getTransactionEntryList()).hasSize(3);

    TransactionEntry expected_transaction1 =
        TransactionEntry.newBuilder()
            .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setEventTimestamp(response.getTransactionEntry(2).getEventTimestamp())
            .setIsCredit(true)
            .setAmount(amount.toString())
            .build();
    TransactionEntry expected_transaction2 =
        TransactionEntry.newBuilder()
            .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setEventTimestamp(response.getTransactionEntry(1).getEventTimestamp())
            .setIsCredit(false)
            .setAmount(amount.toString())
            .build();
    TransactionEntry expected_transaction3 =
        TransactionEntry.newBuilder()
            .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
            .setEventTimestamp(response.getTransactionEntry(0).getEventTimestamp())
            .setIsCredit(true)
            .setAmount(amount.toString())
            .build();
    assertThat(response.getTransactionEntry(2)).isEqualTo(expected_transaction1);
    assertThat(response.getTransactionEntry(1)).isEqualTo(expected_transaction2);
    assertThat(response.getTransactionEntry(0)).isEqualTo(expected_transaction3);

    // Test that if timestamps are not set, we return all transactions
    response =
        finAppService.getRecentTransactionsForAccount(
            GetRecentTransactionsForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .build());
    assertThat(response.getTransactionEntryList()).hasSize(3);

    // Test that beginTimestamp is inclusive.
    response =
        finAppService.getRecentTransactionsForAccount(
            GetRecentTransactionsForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .setBeginTimestamp(expected_transaction1.getEventTimestamp())
                .build());
    assertThat(response.getTransactionEntryList()).hasSize(3);

    // Test that endTimestamp is exclusive.
    response =
        finAppService.getRecentTransactionsForAccount(
            GetRecentTransactionsForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .setEndTimestamp(expected_transaction3.getEventTimestamp())
                .build());
    assertThat(response.getTransactionEntryList()).hasSize(2);

    // Test that limit works and returns most recent transaction.
    response =
        finAppService.getRecentTransactionsForAccount(
            GetRecentTransactionsForAccountRequest.newBuilder()
                .setAccountId(ByteString.copyFrom(fromAccountId.toByteArray()))
                .setMaxEntryCount(2)
                .build());
    assertThat(response.getTransactionEntryList()).hasSize(2);
    assertThat(response.getTransactionEntry(0)).isEqualTo(expected_transaction3);
    assertThat(response.getTransactionEntry(1)).isEqualTo(expected_transaction2);
  }

  @Test
  public void getRecentTransactionsForAccount_BeginAfterEndTimestamp() {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    BigDecimal accountBalance = new BigDecimal(20);
    addActiveAccountRow(accountId, accountBalance, Timestamp.now());
    Exception e =
        assertThrows(
            io.grpc.StatusRuntimeException.class,
            () ->
                finAppService.getRecentTransactionsForAccount(
                    GetRecentTransactionsForAccountRequest.newBuilder()
                        .setAccountId(ByteString.copyFrom(accountId.toByteArray()))
                        .setBeginTimestamp(Timestamp.MAX_VALUE.toProto())
                        .setEndTimestamp(Timestamp.now().toProto())
                        .build()));
    assertThat(e.getMessage()).contains("Invalid timestamp range");
  }
}
