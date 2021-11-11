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
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class SpannerDaoJDBCImpl implements SpannerDaoInterface {

  private final String connectionUrl;

  SpannerDaoJDBCImpl(String spannerProjectId, String spannerInstanceId, String spannerDatabaseId) {
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
      throws StatusException {
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
      throw Status.fromThrowable(e).asException();
    }
  }

  public void createAccount(ByteArray accountId, AccountStatus accountStatus, BigDecimal balance)
      throws StatusException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement ps =
            connection.prepareStatement(
                "INSERT INTO Account\n"
                    + "(AccountId, AccountStatus, Balance, CreationTimestamp)\n"
                    + "VALUES\n"
                    + "(?, ?, ?, PENDING_COMMIT_TIMESTAMP())")) {
      ps.setBytes(1, accountId.toByteArray());
      ps.setInt(2, accountStatus.getNumber());
      ps.setBigDecimal(3, balance);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  public void createCustomerRole(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws StatusException {
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
      throw Status.fromThrowable(e).asException();
    }
  }

  public ImmutableMap<ByteArray, BigDecimal> moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount) throws StatusException {
    if (fromAccountId.equals(toAccountId)) {
      throw Status.INVALID_ARGUMENT
          .withDescription("\"To\" and \"from\" account IDs must be different")
          .asException();
    }
    try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
      connection.setAutoCommit(false);
      ImmutableMap<ByteArray, AccountData> accountData =
          readAccountDataForTransfer(ImmutableList.of(fromAccountId, toAccountId), connection);
      byte[] fromAccountIdArray = fromAccountId.toByteArray();
      byte[] toAccountIdArray = toAccountId.toByteArray();
      BigDecimal sourceAmount = accountData.get(fromAccountId).balance;
      BigDecimal newSourceAmount = sourceAmount.subtract(amount);
      BigDecimal destAmount = accountData.get(toAccountId).balance;
      BigDecimal newDestAmount = destAmount.add(amount);
      if (newSourceAmount.signum() == -1) {
        throw Status.INVALID_ARGUMENT
            .withDescription(
                String.format(
                    "Account balance cannot be negative. original account balance: %s, amount to be"
                        + " removed: %s",
                    sourceAmount, amount))
            .asException();
      }
      updateAccount(fromAccountId.toByteArray(), newSourceAmount, connection);
      updateAccount(toAccountIdArray, newDestAmount, connection);
      insertTransferTransactions(fromAccountIdArray, toAccountIdArray, amount, connection);
      connection.commit();
      return ImmutableMap.of(fromAccountId, newSourceAmount, toAccountId, newDestAmount);
    } catch (SQLException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  public BigDecimal createTransactionForAccount(
      ByteArray accountId, BigDecimal amount, boolean isCredit) throws StatusException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
      connection.setAutoCommit(false);
      byte[] accountIdArray = accountId.toByteArray();
      BigDecimal oldBalance =
          readAccountDataForTransfer(ImmutableList.of(accountId), connection)
              .get(accountId)
              .balance;
      BigDecimal newBalance;
      if (isCredit) {
        newBalance = oldBalance.subtract(amount);
      } else {
        newBalance = oldBalance.add(amount);
      }
      if (newBalance.signum() == -1) {
        throw Status.INVALID_ARGUMENT
            .withDescription(
                String.format(
                    "Account balance cannot be negative. original account balance: %s, amount to be"
                        + " removed: %s",
                    oldBalance, amount))
            .asException();
      }
      updateAccount(accountIdArray, newBalance, connection);
      insertTransaction(accountIdArray, amount, isCredit, connection);
      connection.commit();
      return newBalance;
    } catch (SQLException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  public ImmutableList<TransactionEntry> getRecentTransactionsForAccount(
      ByteArray accountId, Timestamp beginTimestamp, Timestamp endTimestamp, int maxEntryCount)
      throws StatusException {
    try (Connection connection = DriverManager.getConnection(this.connectionUrl);
        PreparedStatement readStatement =
            connection.prepareStatement(
                "SELECT * "
                    + "FROM TransactionHistory "
                    + "WHERE AccountId = ? AND "
                    + "EventTimestamp >= ? AND "
                    + "EventTimestamp < ? "
                    + "ORDER BY EventTimestamp DESC"
                    + (maxEntryCount > 0 ? " LIMIT " + maxEntryCount : ""))) {
      readStatement.setBytes(1, accountId.toByteArray());
      readStatement.setTimestamp(2, beginTimestamp.toSqlTimestamp());
      readStatement.setTimestamp(3, endTimestamp.toSqlTimestamp());
      ResultSet resultSet = readStatement.executeQuery();
      ImmutableList.Builder<TransactionEntry> transactionHistoriesBuilder = ImmutableList.builder();
      while (resultSet.next()) {
        transactionHistoriesBuilder.add(
            TransactionEntry.newBuilder()
                .setAccountId(ByteString.copyFrom(resultSet.getBytes("AccountId")))
                .setEventTimestamp(Timestamp.of(resultSet.getTimestamp("EventTimestamp")).toProto())
                .setIsCredit(resultSet.getBoolean("IsCredit"))
                .setAmount(resultSet.getString("Amount"))
                .build());
      }
      return transactionHistoriesBuilder.build();
    } catch (SQLException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  /**
   * Returns an ImmutableMap of all requested AccountData keyed by account ids.
   *
   * @param accountIds account ids for which to return AccountData
   * @param connection used for queries
   * @return mapping of AccountData, keyed by account id
   * @throws StatusException if any of the requested accountIds is not found, or the associated
   *     accountStatus is not ACTIVE.
   */
  private ImmutableMap<ByteArray, AccountData> readAccountDataForTransfer(
      Iterable<ByteArray> accountIds, Connection connection) throws StatusException {
    ImmutableMap<ByteArray, AccountData> accountDataMap;
    try (PreparedStatement readStatement =
        connection.prepareStatement(
            "SELECT AccountId, AccountStatus, Balance FROM Account WHERE AccountId IN UNNEST(?)")) {
      List<byte[]> accountIdArrays = new ArrayList<>();
      for (ByteArray accountId : accountIds) {
        accountIdArrays.add(accountId.toByteArray());
      }
      readStatement.setArray(1, connection.createArrayOf("BYTES", accountIdArrays.toArray()));
      java.sql.ResultSet resultSet = readStatement.executeQuery();
      ImmutableMap.Builder<ByteArray, AccountData> accountDataBuilder = ImmutableMap.builder();
      while (resultSet.next()) {
        AccountData accountData = new AccountData();
        accountData.balance = resultSet.getBigDecimal("Balance");
        accountData.status = AccountStatus.forNumber((int) resultSet.getLong("AccountStatus"));
        accountDataBuilder.put(ByteArray.copyFrom(resultSet.getBytes("AccountId")), accountData);
      }
      accountDataMap = accountDataBuilder.build();
    } catch (SQLException e) {
      throw Status.fromThrowable(e).asException();
    }
    for (ByteArray accountId : accountIds) {
      if (!accountDataMap.containsKey(accountId)) {
        throw Status.INVALID_ARGUMENT
            .withDescription(String.format("Account not found: %s", accountId.toString()))
            .asException();
      } else if (accountDataMap.get(accountId).status != AccountStatus.ACTIVE) {
        throw Status.INVALID_ARGUMENT
            .withDescription(
                String.format(
                    "Non-active accounts are not eligible for transfers: %s", accountId.toString()))
            .asException();
      }
    }
    return accountDataMap;
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
