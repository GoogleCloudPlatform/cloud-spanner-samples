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
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class SpannerDao {

  // TODO: get these variables from args
  private final String projectId = "test-project";
  private final String databaseId = "test-database";
  private final String instanceId = "test-instance";

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

  // TODO: remove dependency?
  @Inject
  SpannerDao(DatabaseClient databaseClient) {
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
        ps.executeUpdate();
        System.out.printf("Customer created: %s\n", name);
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
                  + "(?, ?, ?, ?, PENDING_COMMIT_TIMESTAMP())")) {
        ps.setBytes(1, accountId.toByteArray());
        ps.setInt(2, accountType.getNumber());
        ps.setInt(3, accountStatus.getNumber());
        ps.setBigDecimal(4, balance);
        int updateCounts = ps.executeUpdate();
        System.out.printf("Account created with balance %s\n", balance.toString());
      }
    }
  }

  void addAccountForCustomer(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws SQLException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "INSERT INTO CustomerRole\n"
                  + "(CustomerId, AccountId, RoleId, Role)\n"
                  + "VALUES\n"
                  + "(?, ?, ?, ?)")) {
        ps.setBytes(1, customerId.toByteArray());
        ps.setBytes(2, accountId.toByteArray());
        ps.setBytes(3, roleId.toByteArray());
        ps.setString(4, roleName);
        int updateCounts = ps.executeUpdate();
        System.out.printf("New role created: %s\n", roleName);
      }
    }
  }


  void moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount)
      throws SQLException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
      connection.setAutoCommit(false);
      try (
          PreparedStatement readStatement = connection.prepareStatement(
              "SELECT AccountId, Balance FROM Account WHERE (AccountId = ? or AccountId = ?)"
          );
          PreparedStatement updateAccountStatement = connection.prepareStatement(
              "UPDATE Account SET Balance = ? WHERE AccountId = ?"
          );
          PreparedStatement insertTransactionStatement = connection.prepareStatement(
              "INSERT INTO TransactionHistory (AccountId, Amount, IsCredit, EventTimestamp)"
                  + "VALUES (?, ?, ?, PENDING_COMMIT_TIMESTAMP())")) {
        readStatement.setBytes(1, fromAccountId.toByteArray());
        readStatement.setBytes(2, toAccountId.toByteArray());
        java.sql.ResultSet resultSet = readStatement.executeQuery();
        ImmutableMap.Builder<byte[], BigDecimal> accountBalancesBuilder = ImmutableMap.builder();
        while (resultSet.next()) {
          accountBalancesBuilder.put(
              resultSet.getBytes("AccountId"), resultSet.getBigDecimal("Balance"));
        }
        ImmutableMap<byte[], BigDecimal> accountBalances = accountBalancesBuilder.build();
        updateAccount(fromAccountId.toByteArray(),
            accountBalances.get(fromAccountId.toByteArray()).subtract(amount),
            updateAccountStatement);
        updateAccount(toAccountId.toByteArray(),
            accountBalances.get(toAccountId.toByteArray()).add(amount), updateAccountStatement);
        updateAccountStatement.executeBatch();
        insertTransaction(fromAccountId.toByteArray(), amount, /* isCredit = */ true,
            insertTransactionStatement); //TODO: are these bools correct?
        insertTransaction(toAccountId.toByteArray(), amount, /* isCredit = */ false,
            insertTransactionStatement);
        insertTransactionStatement.executeBatch();
        connection.commit();
        System.out.printf("Balance of %s moved.\n", amount.toString());
      }
    }
  }

  void getAccountMetadata(ByteArray accountId) throws SpannerException {

  }


  private void updateAccount(byte[] accountId, BigDecimal newBalance,
      PreparedStatement ps) throws SQLException {
    ps.setBigDecimal(1, newBalance);
    ps.setBytes(2, accountId);
    ps.addBatch();
  }

  private void insertTransaction(byte[] accountId, BigDecimal amount, boolean isCredit,
      PreparedStatement ps) throws SQLException {
    ps.setBytes(1, accountId);
    ps.setBigDecimal(2, amount);
    ps.setBoolean(3, isCredit);
    ps.addBatch();
  }
}
