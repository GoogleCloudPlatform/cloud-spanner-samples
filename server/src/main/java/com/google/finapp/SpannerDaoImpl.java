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
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
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

final class SpannerDaoImpl implements SpannerDaoInterface {

  private final DatabaseClient databaseClient;

  SpannerDaoImpl(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  @Override
  public void createCustomer(ByteArray customerId, String name, String address)
      throws StatusException {
    try {
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
    } catch (SpannerException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  @Override
  public void createAccount(ByteArray accountId, AccountStatus accountStatus, BigDecimal balance)
      throws StatusException {
    try {
      databaseClient.write(
          ImmutableList.of(
              Mutation.newInsertBuilder("Account")
                  .set("AccountId")
                  .to(accountId)
                  .set("AccountStatus")
                  .to(accountStatus.getNumber())
                  .set("Balance")
                  .to(balance)
                  .set("CreationTimestamp")
                  .to(Value.COMMIT_TIMESTAMP)
                  .build()));
    } catch (SpannerException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  @Override
  public void createCustomerRole(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws StatusException {
    try {
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
    } catch (SpannerException e) {
      throw Status.fromThrowable(e).asException();
    }
  }

  @Override
  public ImmutableMap<ByteArray, BigDecimal> moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount) throws StatusException {
    if (fromAccountId.equals(toAccountId)) {
      throw Status.INVALID_ARGUMENT
          .withDescription("\"To\" and \"from\" account IDs must be different")
          .asException();
    }
    try {
      return databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                // Note that the transaction can run multiple times, we create
                // accountBalancesBuilder inside the transaction to avoid
                // setting the same key twice below.
                ImmutableMap.Builder<ByteArray, BigDecimal> accountBalancesBuilder = ImmutableMap.builder();
                // Get account balances.
                ImmutableMap<ByteArray, AccountData> accountData =
                    readAccountDataForTransfer(
                        ImmutableList.of(fromAccountId, toAccountId), transaction);

                BigDecimal newSourceAmount =
                    accountData.get(fromAccountId).balance.subtract(amount);
                BigDecimal newDestAmount = accountData.get(toAccountId).balance.add(amount);

                if (newSourceAmount.signum() == -1) {
                  throw Status.INVALID_ARGUMENT
                      .withDescription(
                          String.format(
                              "Account balance cannot be negative. Original account balance: %s,"
                                  + " amount to be removed: %s",
                              accountData.get(fromAccountId).balance.toString(), amount.toString()))
                      .asException();
                }

                transaction.buffer(
                    ImmutableList.of(
                        buildUpdateAccountMutation(fromAccountId, newSourceAmount),
                        buildUpdateAccountMutation(toAccountId, newDestAmount),
                        buildInsertTransactionHistoryMutation(
                            fromAccountId, amount, /* isCredit= */ true),
                        buildInsertTransactionHistoryMutation(
                            toAccountId, amount, /* isCredit= */ false)));

                accountBalancesBuilder.put(fromAccountId, newSourceAmount);
                accountBalancesBuilder.put(toAccountId, newDestAmount);
                return accountBalancesBuilder.build();
              });
    } catch (SpannerException e) {
      // filter for StatusException thrown in lambda function above
      Throwable cause = e.getCause();
      if (cause instanceof StatusException) {
        throw (StatusException) cause;
      }
      throw Status.fromThrowable(e).asException();
    }
  }

  @Override
  public BigDecimal createTransactionForAccount(
      ByteArray accountId, BigDecimal amount, boolean isCredit) throws StatusException {
    try {
      return databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                AccountData accountData =
                    readAccountDataForTransfer(ImmutableList.of(accountId), transaction)
                        .get(accountId);
                BigDecimal newBalance;
                if (isCredit) {
                  newBalance = accountData.balance.subtract(amount);
                } else {
                  newBalance = accountData.balance.add(amount);
                }

                if (newBalance.signum() == -1) {
                  throw Status.INVALID_ARGUMENT
                      .withDescription(
                          String.format(
                              "Account balance cannot be negative. original account balance:"
                                  + " %s, amount to be removed: %s",
                              accountData.balance, amount))
                      .asException();
                }
                transaction.buffer(
                    ImmutableList.of(
                        buildUpdateAccountMutation(accountId, newBalance),
                        buildInsertTransactionHistoryMutation(accountId, amount, isCredit)));
                return newBalance;
              });
    } catch (SpannerException e) {
      // filter for StatusException thrown in lambda function above
      Throwable cause = e.getCause();
      if (cause instanceof StatusException) {
        throw (StatusException) cause;
      }
      throw Status.fromThrowable(e).asException();
    }
  }

  @Override
  public ImmutableList<TransactionEntry> getRecentTransactionsForAccount(
      ByteArray accountId, Timestamp beginTimestamp, Timestamp endTimestamp, int maxEntryCount)
      throws StatusException {
    Statement statement =
        Statement.newBuilder(
                "SELECT * "
                    + "FROM TransactionHistory "
                    + "WHERE AccountId = @accountId AND "
                    + "EventTimestamp >= @beginTimestamp AND "
                    + "EventTimestamp < @endTimestamp "
                    + "ORDER BY EventTimestamp DESC"
                    + (maxEntryCount > 0 ? " LIMIT " + maxEntryCount : ""))
            .bind("accountId")
            .to(accountId)
            .bind("beginTimestamp")
            .to(beginTimestamp.toString())
            .bind("endTimestamp")
            .to(endTimestamp.toString())
            .build();
    try (ResultSet resultSet = databaseClient.singleUse().executeQuery(statement)) {
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
    ResultSet resultSet =
        transaction.read(
            "Account",
            keySetBuilder.build(),
            ImmutableList.of("AccountId", "AccountStatus", "Balance"));

    ImmutableMap.Builder<ByteArray, AccountData> accountDataBuilder = ImmutableMap.builder();
    while (resultSet.next()) {
      AccountData accountData = new AccountData();
      accountData.balance = resultSet.getBigDecimal("Balance");
      accountData.status = AccountStatus.forNumber((int) resultSet.getLong("AccountStatus"));
      accountDataBuilder.put(resultSet.getBytes("AccountId"), accountData);
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
                    "Non-active accounts are not eligible for transfers: %s", accountId.toString()))
            .asException();
      }
    }
    return accountData;
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
