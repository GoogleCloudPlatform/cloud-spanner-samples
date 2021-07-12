// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.finapp;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class SpannerDao {

  private final DatabaseClient databaseClient;

  // TODO(developer): change these variables
  private final String projectId = "test-project";
  private final String databaseId = "test-database";
  private final String instanceId  = "test-instance";

  // use this URL to connect to Cloud Spanner
  // private final String connectionUrl =
  //     String.format(
  //         "jdbc:cloudspanner:/projects/%s/instances/%s/databases/%s",
  //         projectId, instanceId, databaseId);

  // use this URL to connect to the emulator
  private final String connectionUrl =
      String.format(
          "jdbc:cloudspanner://localhost:9010/projects/%s/instances/%s/databases/%s;usePlainText=true",
          projectId, instanceId, databaseId);

  @Inject
  SpannerDao(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  void createCustomer(ByteArray customerId, String name, String address) throws SQLException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "INSERT INTO Customer\n"
                  + "(CustomerId, Name, Address)\n"
                  + "VALUES\n"
                  + "(?, ?, ?)")) {
        ps.setBytes(1, customerId.toByteArray());
        ps.setString(2, name);
        ps.setString(3, address);
        int updateCounts = ps.executeUpdate();
        System.out.printf("Insert counts: %d", updateCounts);
      }
    }
  }


  void createAccount(
      ByteArray accountId, AccountType accountType, AccountStatus accountStatus, BigDecimal balance)
      throws SQLException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "INSERT INTO Account\n"
                  + "(AccountId, AccountType, AccountStatus, Balance, CreationTimestamp)\n"
                  + "VALUES\n"
                  + "(?, ?, ?, ?, ?)")) {
        ps.setBytes(1, accountId.toByteArray());
        ps.setInt(2, accountType.getNumber());
        ps.setInt(3, accountStatus.getNumber());
        ps.setBigDecimal(4, balance);
        ps.setTimestamp(5, Value.COMMIT_TIMESTAMP.toSqlTimestamp()); //TODO: should we use this method to get timestamp?
        int updateCounts = ps.executeUpdate();
        System.out.printf("Insert counts: %d", updateCounts);
      }
    }
  }

  void addAccountForCustomer(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws SpannerException {
    databaseClient.write(
        ImmutableList.of(
            Mutation.newInsertBuilder("CustomerRole")
                .set("CustomerId")
                .to(customerId)
                .set("AccountId")
                .to(accountId)
                .set("RoleId")
                .to(roleId)
                .set("Role")
                .to(roleName)
                .build()));
  }

  void moveAccountBalance(ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount) throws SpannerException {
    databaseClient
        .readWriteTransaction()
        .run(
            transaction -> {
              // Get account balances.
              ImmutableMap<ByteArray, BigDecimal> accountBalances =
                  readAccountBalances(fromAccountId, toAccountId, transaction);

              transaction.buffer(
                  ImmutableList.of(
                      buildUpdateAccountMutation(
                          fromAccountId, accountBalances.get(fromAccountId).subtract(amount)),
                      buildUpdateAccountMutation(
                          toAccountId, accountBalances.get(toAccountId).add(amount)),
                      buildInsertTransactionHistoryMutation(
                          fromAccountId, amount, /* isCredit= */ true),
                      buildInsertTransactionHistoryMutation(
                          toAccountId, amount, /* isCredit= */ false)));
              return null;
            });
  }

  void getAccountMetadata(ByteArray accountId) throws SpannerException {

  }

  private ImmutableMap<ByteArray, BigDecimal> readAccountBalances(
      ByteArray fromAccountId, ByteArray toAccountId, TransactionContext transaction) {
    ResultSet resultSet =
        transaction.read(
            "Account",
            KeySet.newBuilder().addKey(Key.of(fromAccountId)).addKey(Key.of(toAccountId)).build(),
            ImmutableList.of("AccountId", "Balance"));

    ImmutableMap.Builder<ByteArray, BigDecimal> accountBalancesBuilder = ImmutableMap.builder();
    while (resultSet.next()) {
      accountBalancesBuilder.put(
          resultSet.getBytes("AccountId"), resultSet.getBigDecimal("Balance"));
    }
    return accountBalancesBuilder.build();
  }

  private Mutation buildUpdateAccountMutation(ByteArray accountId, BigDecimal newBalance) {
    return Mutation.newUpdateBuilder("Account")
        .set("AccountId")
        .to(accountId)
        .set("Balance")
        .to(newBalance)
        .build();
  }

  private Mutation buildInsertTransactionHistoryMutation(
      ByteArray accountId, BigDecimal amount, boolean isCredit) {
    return Mutation.newInsertBuilder("TransactionHistory")
        .set("AccountId")
        .to(accountId)
        .set("Amount")
        .to(amount)
        .set("IsCredit")
        .to(isCredit)
        .set("EventTimestamp")
        .to(Value.COMMIT_TIMESTAMP)
        .build();
  }
}
