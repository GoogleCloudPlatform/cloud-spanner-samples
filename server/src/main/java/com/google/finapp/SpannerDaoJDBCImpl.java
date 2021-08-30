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
import com.google.cloud.Timestamp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

final class SpannerDaoJDBCImpl implements SpannerDaoInterface {

  private final String connectionUrl;

  @Inject
  SpannerDaoJDBCImpl(
      @ArgsModule.SpannerProjectId String spannerProjectId,
      @ArgsModule.SpannerInstanceId String spannerInstanceId,
      @ArgsModule.SpannerDatabaseId String spannerDatabaseId) {
    String emulatorHost = System.getenv("SPANNER_EMULATOR_HOST");
    if (emulatorHost != null) {
      // connect to emulator
      this.connectionUrl =
          String.format(
              "jdbc:cloudspanner://%s/projects/%s/instances/%s/databases/%s;usePlainText=true",
              emulatorHost, spannerProjectId, spannerInstanceId, spannerDatabaseId);
    } else {
      // connect to Cloud Spanner
      this.connectionUrl =
          String.format(
              "jdbc:cloudspanner:/projects/%s/instances/%s/databases/%s",
              spannerProjectId, spannerInstanceId, spannerDatabaseId);
    }
  }

  public void createCustomer(ByteArray customerId, String name, String address)
      throws SpannerDaoException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement ps =
            connection.prepareStatement(
                "INSERT INTO Customer\n"
                    + "(CustomerId, Name, Address)\n"
                    + "VALUES\n"
                    + "(?, ?, ?)")) {
      ps.setBytes(1, customerId.toByteArray());
      ps.setString(2, name);
      ps.setString(3, address);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new SpannerDaoException(e);
    }
  }

  public void createAccount(
      ByteArray accountId, AccountType accountType, AccountStatus accountStatus, BigDecimal balance)
      throws SpannerDaoException {
    if (balance.signum() == -1) {
      throw new IllegalArgumentException(
          String.format(
              "Account balance cannot be negative. accountId: %s, balance: %s",
              accountId.toString(), balance.toString()));
    }
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement ps =
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
    } catch (SQLException e) {
      throw new SpannerDaoException(e);
    }
  }

  public void createCustomerRole(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws SpannerDaoException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement ps =
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
    } catch (SQLException e) {
      throw new SpannerDaoException(e);
    }
  }

  public ImmutableMap<ByteArray, BigDecimal> moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount)
      throws SpannerDaoException {
    if (amount.signum() == -1) {
      throw new IllegalArgumentException(
          String.format("Amount transferred cannot be negative. amount: %s", amount.toString()));
    }
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement readStatement =
            connection.prepareStatement(
                "SELECT AccountId, Balance FROM Account WHERE (AccountId = ? or AccountId = ?)")) {
      connection.setAutoCommit(false);
      byte[] fromAccountIdArray = fromAccountId.toByteArray();
      byte[] toAccountIdArray = toAccountId.toByteArray();
      readStatement.setBytes(1, fromAccountIdArray);
      readStatement.setBytes(2, toAccountIdArray);
      java.sql.ResultSet resultSet = readStatement.executeQuery();
      BigDecimal sourceAmount = null;
      BigDecimal destAmount = null;
      while (resultSet.next()) {
        byte[] currentId = resultSet.getBytes("AccountId");
        if (Arrays.equals(currentId, fromAccountIdArray)) {
          sourceAmount = resultSet.getBigDecimal("Balance");
        } else {
          destAmount = resultSet.getBigDecimal("Balance");
        }
      }
      if (sourceAmount == null) {
        throw new IllegalArgumentException(
            String.format("Account not found: %s", fromAccountId.toString()));
      } else if (destAmount == null) {
        throw new IllegalArgumentException(
            String.format("Account not found: %s", toAccountId.toString()));
      }
      BigDecimal newSourceAmount = sourceAmount.subtract(amount);
      BigDecimal newDestAmount = destAmount.add(amount);
      if (newSourceAmount.signum() == -1) {
        throw new IllegalArgumentException(
            String.format(
                "Account balance cannot be negative. original account balance: %s, amount to be removed: %s",
                sourceAmount.toString(), amount.toString()));
      }
      updateAccount(fromAccountIdArray, newSourceAmount, connection);
      updateAccount(toAccountIdArray, newDestAmount, connection);
      insertTransferTransactions(fromAccountIdArray, toAccountIdArray, amount, connection);
      connection.commit();
      return ImmutableMap.of(fromAccountId, newSourceAmount, toAccountId, newDestAmount);
    } catch (SQLException e) {
      throw new SpannerDaoException(e);
    }
  }

  public BigDecimal createTransactionForAccount(
      ByteArray accountId, BigDecimal amount, boolean isCredit) throws SpannerDaoException {
    if (amount.signum() == -1) {
      throw new IllegalArgumentException(
          String.format("Amount transferred cannot be negative. amount: %s", amount.toString()));
    }
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement readStatement =
            connection.prepareStatement("SELECT Balance FROM Account WHERE AccountId = ?")) {
      connection.setAutoCommit(false);
      byte[] accountIdArray = accountId.toByteArray();
      readStatement.setBytes(1, accountIdArray);
      java.sql.ResultSet resultSet = readStatement.executeQuery();
      BigDecimal oldBalance = null;
      while (resultSet.next()) {
        oldBalance = resultSet.getBigDecimal("Balance");
      }
      if (oldBalance == null) {
        throw new IllegalArgumentException(
            String.format("Account not found: %s", accountId.toString()));
      }
      BigDecimal newBalance;
      if (isCredit) {
        newBalance = oldBalance.subtract(amount);
      } else {
        newBalance = oldBalance.add(amount);
      }
      if (newBalance.signum() == -1) {
        throw new IllegalArgumentException(
            String.format(
                "Account balance cannot be negative. original account balance: %s, amount to be removed: %s",
                oldBalance.toString(), amount.toString()));
      }
      updateAccount(accountIdArray, newBalance, connection);
      insertTransaction(accountIdArray, amount, isCredit, connection);
      connection.commit();
      return newBalance;
    } catch (SQLException e) {
      throw new SpannerDaoException(e);
    }
  }

  public ImmutableList<TransactionEntry> getRecentTransactionsForAccount(
      ByteArray accountId, Timestamp beginTimestamp, Timestamp endTimestamp)
      throws SpannerDaoException {
    if (beginTimestamp.compareTo(endTimestamp) > 0) {
      throw new IllegalArgumentException(
          String.format("Invalid timestamp range. %s is after %s.", beginTimestamp, endTimestamp));
    }
    if (endTimestamp.compareTo(beginTimestamp) < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid timestamp range. %s is before %s.", endTimestamp, beginTimestamp));
    }
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement readStatement =
            connection.prepareStatement(
                "SELECT * "
                    + "FROM TransactionHistory "
                    + "WHERE AccountId = ? AND EventTimestamp BETWEEN ? AND ? "
                    + "ORDER BY EventTimestamp DESC")) {
      readStatement.setBytes(1, accountId.toByteArray());
      readStatement.setTimestamp(2, beginTimestamp.toSqlTimestamp());
      readStatement.setTimestamp(3, endTimestamp.toSqlTimestamp());
      ResultSet resultSet = readStatement.executeQuery();
      ImmutableList.Builder<TransactionEntry> transactionHistoriesBuilder = ImmutableList.builder();
      while (resultSet.next()) {
        transactionHistoriesBuilder.add(
            TransactionEntry.newBuilder()
                .setAccountId(ByteString.copyFrom(resultSet.getBytes("AccountId")))
                .setEventTimestamp(
                    // use a builder to set com.google.protobuf.Timestamp from java.sql.Timestamp
                    // object
                    com.google.protobuf.Timestamp.newBuilder()
                        .setNanos(resultSet.getTimestamp("EventTimestamp").getNanos())
                        .setSeconds(resultSet.getTimestamp("EventTimestamp").getSeconds()))
                .setIsCredit(resultSet.getBoolean("IsCredit"))
                .setAmount(resultSet.getString("Amount"))
                .build());
      }
      return transactionHistoriesBuilder.build();
    } catch (SQLException e) {
      throw new SpannerDaoException(e);
    }
  }

  private void updateAccount(byte[] accountId, BigDecimal newBalance, Connection connection)
      throws SQLException {
    try (PreparedStatement preparedStatement =
        connection.prepareStatement("UPDATE Account SET Balance = ? WHERE AccountId = ?")) {
      preparedStatement.setBigDecimal(1, newBalance);
      preparedStatement.setBytes(2, accountId);
      preparedStatement.executeUpdate();
    }
  }

  private void insertTransferTransactions(
      byte[] fromAccountId, byte[] toAccountId, BigDecimal amount, Connection connection)
      throws SQLException {
    try (PreparedStatement preparedStatement =
        connection.prepareStatement(
            "INSERT INTO TransactionHistory (AccountId, Amount, IsCredit, EventTimestamp)"
                + "VALUES (?, ?, ?, PENDING_COMMIT_TIMESTAMP()),"
                + "(?, ?, ?, PENDING_COMMIT_TIMESTAMP())")) {
      preparedStatement.setBytes(1, fromAccountId);
      preparedStatement.setBigDecimal(2, amount);
      preparedStatement.setBoolean(3, /* isCredit = */ true);
      preparedStatement.setBytes(4, toAccountId);
      preparedStatement.setBigDecimal(5, amount);
      preparedStatement.setBoolean(6, /* isCredit = */ false);
      preparedStatement.executeUpdate();
    }
  }

  private void insertTransaction(
      byte[] accountId, BigDecimal amount, boolean isCredit, Connection connection)
      throws SQLException {
    try (PreparedStatement preparedStatement =
        connection.prepareStatement(
            "INSERT INTO TransactionHistory (AccountId, Amount, IsCredit, EventTimestamp)"
                + "VALUES (?, ?, ?, PENDING_COMMIT_TIMESTAMP())")) {
      preparedStatement.setBytes(1, accountId);
      preparedStatement.setBigDecimal(2, amount);
      preparedStatement.setBoolean(3, isCredit);
      preparedStatement.executeUpdate();
    }
  }
}
