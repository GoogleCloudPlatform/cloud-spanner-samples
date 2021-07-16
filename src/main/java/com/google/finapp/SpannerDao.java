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
import java.util.Arrays;

final class SpannerDao {

  private final String projectId;
  private final String databaseId;
  private final String instanceId;
  private final String connectionUrl;

  @Inject
  SpannerDao(@ArgsModule.SpannerProjectId String spannerProjectId,
      @ArgsModule.SpannerInstanceId String spannerInstanceId,
      @ArgsModule.SpannerDatabaseId String spannerDatabaseId) {
    this.projectId = spannerProjectId;
    this.databaseId = spannerDatabaseId;
    this.instanceId = spannerInstanceId;
    // use this URL to connect to the emulator
    this.connectionUrl = String.format(
        "jdbc:cloudspanner://localhost:9010/projects/%s/instances/%s/databases/%s;usePlainText=true",
        projectId, instanceId, databaseId);
    // use this URL to connect to Cloud Spanner
    // this.connectionUrl = String.format(
    //     "jdbc:cloudspanner:/projects/%s/instances/%s/databases/%s",
    //     projectId, instanceId, databaseId);
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
        ps.executeUpdate();
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
        ps.executeUpdate();
        System.out.printf("New role created: %s\n", roleName);
      }
    }
  }


  void moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount)
      throws SQLException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
      // begin transaction
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
                  + "VALUES (?, ?, ?, PENDING_COMMIT_TIMESTAMP()), "
                  + "(?, ?, ?, PENDING_COMMIT_TIMESTAMP())")) {
        byte[] fromAccountIdArray = fromAccountId.toByteArray();
        byte[] toAccountIdArray = toAccountId.toByteArray();
        BigDecimal[] accountBalances = readAccountBalances(
            fromAccountIdArray, toAccountIdArray, readStatement);
        updateAccount(fromAccountIdArray,
            accountBalances[0].subtract(amount),
            updateAccountStatement);
        updateAccount(toAccountIdArray,
            accountBalances[1].add(amount), updateAccountStatement);
        updateAccountStatement.executeBatch();
        insertTransaction(
            fromAccountIdArray, toAccountIdArray, amount,
            insertTransactionStatement);
        insertTransactionStatement.executeUpdate();
        connection.commit();
        System.out.printf("Balance of %s moved.\n", amount.toString());
      }
    }
  }

  void getAccountMetadata(ByteArray accountId) throws SpannerException {

  }


  private BigDecimal[] readAccountBalances(
      byte[] fromAccountId, byte[] toAccountId, PreparedStatement preparedStatement)
      throws SQLException {
    // preparedStatement: "SELECT AccountId, Balance FROM Account WHERE (AccountId = ? or AccountId = ?)"
    preparedStatement.setBytes(1, fromAccountId);
    preparedStatement.setBytes(2, toAccountId);
    java.sql.ResultSet resultSet = preparedStatement.executeQuery();
    BigDecimal[] results = new BigDecimal[2];
    while (resultSet.next()) {
      byte[] currentId = resultSet.getBytes("AccountId");
      if (Arrays.equals(currentId, fromAccountId)) {
        results[0] = resultSet.getBigDecimal("Balance");
      } else {
        results[1] = resultSet.getBigDecimal("Balance");
      }
    }
    return results;
  }


  private void updateAccount(byte[] accountId, BigDecimal newBalance,
      PreparedStatement preparedStatement) throws SQLException {
    // preparedStatement: "UPDATE Account SET Balance = ? WHERE AccountId = ?"
    preparedStatement.setBigDecimal(1, newBalance);
    preparedStatement.setBytes(2, accountId);
    preparedStatement.addBatch();
  }

  private void insertTransaction(byte[] fromAccountId, byte[] toAccountId, BigDecimal amount,
      PreparedStatement preparedStatement) throws SQLException {
    /* preparedStatement: "INSERT INTO TransactionHistory (AccountId, Amount, IsCredit, EventTimestamp)"
                       + "VALUES (?, ?, ?, PENDING_COMMIT_TIMESTAMP()),"
                       + "(?, ?, ?, PENDING_COMMIT_TIMESTAMP())"
     */
    preparedStatement.setBytes(1, fromAccountId);
    preparedStatement.setBigDecimal(2, amount);
    preparedStatement.setBoolean(3, /* isCredit = */ true);
    preparedStatement.setBytes(4, toAccountId);
    preparedStatement.setBigDecimal(5, amount);
    preparedStatement.setBoolean(6, /* isCredit = */ false);
    preparedStatement.addBatch();
  }
}

