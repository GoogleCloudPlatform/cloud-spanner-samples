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

// From SpannerDaoimpl
import com.google.cloud.ByteArray;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusException;
import java.math.BigDecimal;

final class SpannerDaoPGImpl implements SpannerDaoInterface {

  private final DatabaseClient databaseClient;

  SpannerDaoPGImpl(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public void createCustomer(ByteArray customerId, String name, String address)
      throws StatusException {
    try {
      this.databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                Statement statement =
                    Statement.newBuilder(
                            "INSERT INTO Customer\n"
                                + "(CustomerId, Name, Address)\n"
                                + "VALUES\n"
                                + "(@customerId, @name, @address)")
                        .bind("customerId")
                        .to(customerId)
                        .bind("name")
                        .to(name)
                        .bind("address")
                        .to(address)
                        .build();
                transaction.executeUpdate(statement);
                return null;
              });
    } catch (SpannerException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  public void createAccount(ByteArray accountId, AccountStatus accountStatus, BigDecimal balance)
      throws StatusException {
    try {
      this.databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                Statement statement =
                    Statement.newBuilder(
                            "INSERT INTO Account\n"
                                + "(AccountId, AccountStatus, Balance, CreationTimestamp)\n"
                                + "VALUES\n"
                                + "($1, $2, $3, SPANNER.PENDING_COMMIT_TIMESTAMP())")
                        .bind("p1")
                        .to(accountId)
                        .bind("p2")
                        .to(accountStatus.getNumber())
                        .bind("p3")
                        .to(
                            Value.pgNumeric(
                                balance.toString())) // Numeric requires special handling
                        .build();
                transaction.executeUpdate(statement);

                return null;
              });
    } catch (SpannerException e) {
      System.out.println(e.getMessage());
      throw Status.fromThrowable(e).asException();
    }
  }

  public void createCustomerRole(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws StatusException {

    try {
      this.databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                Statement statement =
                    Statement.newBuilder(
                            "INSERT INTO CustomerRole\n"
                                + "(CustomerId, AccountId, RoleId, Role)\n"
                                + "VALUES\n"
                                + "($1, $2, $3, $4)")
                        .bind("p1")
                        .to(customerId)
                        .bind("p2")
                        .to(accountId)
                        .bind("p3")
                        .to(roleId)
                        .bind("p4")
                        .to(roleName)
                        .build();
                transaction.executeUpdate(statement);

                return null;
              });
    } catch (SpannerException e) {
      System.out.println(e.getMessage());
      throw Status.fromThrowable(e).asException();
    }
  }

  public ImmutableMap<ByteArray, BigDecimal> moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount) throws StatusException {
    try {
      return this.databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                ImmutableMap<ByteArray, AccountData> accountData =
                    readAccountDataForTransfer(
                        ImmutableList.of(fromAccountId, toAccountId), transaction);
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

                updateAccount(fromAccountId, newSourceAmount, transaction);
                updateAccount(toAccountId, newDestAmount, transaction);
                insertTransferTransactions(fromAccountId, toAccountId, amount, transaction);
                return ImmutableMap.of(fromAccountId, newSourceAmount, toAccountId, newDestAmount);
              });
    } catch (SpannerException e) {
      e.printStackTrace();
      throw Status.fromThrowable(e).asException();
    }
  }

  public BigDecimal createTransactionForAccount(
      ByteArray accountId, BigDecimal amount, boolean isCredit) throws StatusException {
    try {
      return this.databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                BigDecimal oldBalance =
                    readAccountDataForTransfer(ImmutableList.of(accountId), transaction)
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
                updateAccount(accountId, newBalance, transaction);
                insertTransaction(accountId, amount, isCredit, transaction);
                return newBalance;
              });
    } catch (SpannerException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  public ImmutableList<TransactionEntry> getRecentTransactionsForAccount(
      ByteArray accountId, Timestamp beginTimestamp, Timestamp endTimestamp, int maxEntryCount)
      throws StatusException {
    Statement statement =
        Statement.newBuilder(
                "SELECT * "
                    + "FROM TransactionHistory "
                    + "WHERE AccountId = $1 AND "
                    + "EventTimestamp >= $2 AND "
                    + "EventTimestamp < $3 "
                    + "ORDER BY EventTimestamp DESC"
                    + (maxEntryCount > 0 ? " LIMIT " + maxEntryCount : ""))
            .bind("p1")
            .to(accountId)
            .bind("p2")
            .to(beginTimestamp.toString())
            .bind("p3")
            .to(endTimestamp.toString())
            .build();
    try (ResultSet resultSet = this.databaseClient.singleUse().executeQuery(statement)) {
      ImmutableList.Builder<TransactionEntry> transactionHistoriesBuilder = ImmutableList.builder();
      while (resultSet.next()) {
        transactionHistoriesBuilder.add(
            TransactionEntry.newBuilder()
                .setAccountId(ByteString.copyFrom(resultSet.getBytes("AccountId").toByteArray()))
                .setEventTimestamp(resultSet.getTimestamp("EventTimestamp").toProto())
                .setIsCredit(resultSet.getBoolean("IsCredit"))
                .setAmount(resultSet.getBigDecimal("Amount").toString())
                .build());
      }
      return transactionHistoriesBuilder.build();
    } catch (SpannerException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  /**
   * Returns an ImmutableMap of all requested AccountData keyed by account ids.
   *
   * @param accountIds account ids for which to return AccountData
   * @param transaction the transaction context to use
   * @return mapping of AccountData, keyed by account id
   * @throws StatusException if any of the requested accountIds is not found, or the associated
   *     accountStatus is not ACTIVE.
   */
  private ImmutableMap<ByteArray, AccountData> readAccountDataForTransfer(
      Iterable<ByteArray> accountIds, TransactionContext transaction) throws StatusException {
    KeySet.Builder keySetBuilder = KeySet.newBuilder();
    for (ByteArray accountId : accountIds) {
      keySetBuilder.addKey(Key.of(accountId));
    }

    try (ResultSet resultSet =
        transaction.read(
            "Account",
            keySetBuilder.build(),
            ImmutableList.of("accountid", "accountstatus", "balance"))) {
      ImmutableMap.Builder<ByteArray, AccountData> accountDataBuilder = ImmutableMap.builder();
      while (resultSet.next()) {
        AccountData accountData = new AccountData();
        accountData.balance = new BigDecimal(String.valueOf(resultSet.getValue("balance")));
        accountData.status = AccountStatus.forNumber((int) resultSet.getLong("accountstatus"));
        accountDataBuilder.put(resultSet.getBytes("accountid"), accountData);
      }
      ImmutableMap<ByteArray, AccountData> accountData = accountDataBuilder.build();
      for (ByteArray accountId : accountIds) {
        if (!accountData.containsKey(accountId)) {
          throw Status.INVALID_ARGUMENT
              .withDescription(String.format("Account not found: %s", accountId.toString()))
              .asException();
        } else if (accountData.get(accountId).status != AccountStatus.ACTIVE) {
          throw Status.INVALID_ARGUMENT
              .withDescription(
                  String.format(
                      "Non-active accounts are not eligible for transfers: %s",
                      accountId.toString()))
              .asException();
        }
      }
      return accountData;
    } catch (SpannerException e) {
      System.out.println(e.toString());
      // filter for StatusException thrown in lambda function above
      Throwable cause = e.getCause();
      if (cause instanceof StatusException) {
        throw (StatusException) cause;
      }
      throw Status.fromThrowable(e).asException();
    }
  }

  private void updateAccount(
      ByteArray accountId, BigDecimal newBalance, TransactionContext transaction)
      throws SpannerException {
    try {
      Statement statement =
          Statement.newBuilder("UPDATE Account SET Balance = $1 WHERE AccountId = $2")
              .bind("p1")
              .to(Value.pgNumeric(newBalance.toString())) // Numeric requires special handling
              .bind("p2")
              .to(accountId)
              .build();
      transaction.executeUpdate(statement);
    } catch (SpannerException e) {
      throw e;
    }
  }

  private void insertTransferTransactions(
      ByteArray fromAccountId,
      ByteArray toAccountId,
      BigDecimal amount,
      TransactionContext transaction)
      throws SpannerException {
    try {
      Statement statement =
          Statement.newBuilder(
                  "INSERT INTO TransactionHistory (AccountId, Amount, IsCredit, EventTimestamp)"
                      + "VALUES ($1, $2, $3, SPANNER.PENDING_COMMIT_TIMESTAMP()),"
                      + "($4, $5, $6, SPANNER.PENDING_COMMIT_TIMESTAMP())")
              .bind("p1")
              .to(fromAccountId)
              .bind("p2")
              .to(Value.pgNumeric(amount.toString())) // Numeric requires special handling
              .bind("p3")
              .to(/* isCredit = */ true)
              .bind("p4")
              .to(toAccountId)
              .bind("p5")
              .to(Value.pgNumeric(amount.toString())) // Numeric requires special handling
              .bind("p6")
              .to(/* isCredit = */ false)
              .build();
      transaction.executeUpdate(statement);
    } catch (SpannerException e) {
      throw e;
    }
  }

  private void insertTransaction(
      ByteArray accountId, BigDecimal amount, boolean isCredit, TransactionContext transaction)
      throws SpannerException {
    try {
      Statement statement =
          Statement.newBuilder(
                  "INSERT INTO TransactionHistory (AccountId, Amount, IsCredit, EventTimestamp)"
                      + "VALUES ($1, $2, $3, SPANNER.PENDING_COMMIT_TIMESTAMP())")
              .bind("p1")
              .to(accountId)
              .bind("p2")
              .to(Value.pgNumeric(amount.toString())) // Numeric requires special handling
              .bind("p3")
              .to(isCredit)
              .build();
      transaction.executeUpdate(statement);
    } catch (SpannerException e) {
      throw e;
    }
  }
}
