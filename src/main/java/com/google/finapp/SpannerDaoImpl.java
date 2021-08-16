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
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.math.BigDecimal;

final class SpannerDaoImpl implements SpannerDaoInterface {

  private final DatabaseClient databaseClient;

  @Inject
  SpannerDaoImpl(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  @Override
  public void createCustomer(ByteArray customerId, String name, String address)
      throws SpannerDaoException {
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
      throw new SpannerDaoException(e);
    }
  }

  @Override
  public void createAccount(
      ByteArray accountId, AccountType accountType, AccountStatus accountStatus, BigDecimal balance)
      throws SpannerDaoException {
    if (balance.signum() == -1) {
      throw new IllegalArgumentException(
          String.format(
              "Account balance cannot be negative. accountId: %s, balance: %s",
              accountId.toString(), balance.toString()));
    }
    try {
      databaseClient.write(
          ImmutableList.of(
              Mutation.newInsertBuilder("Account")
                  .set("AccountId")
                  .to(accountId)
                  .set("AccountType")
                  .to(accountType.getNumber())
                  .set("AccountStatus")
                  .to(accountStatus.getNumber())
                  .set("Balance")
                  .to(balance)
                  .set("CreationTimestamp")
                  .to(Value.COMMIT_TIMESTAMP)
                  .build()));
    } catch (SpannerException e) {
      throw new SpannerDaoException(e);
    }
  }

  @Override
  public void createCustomerRole(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws SpannerDaoException {
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
      throw new SpannerDaoException(e);
    }
  }

  @Override
  public ImmutableMap<ByteArray, BigDecimal> moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount)
      throws SpannerDaoException {
    if (amount.signum() == -1) {
      throw new IllegalArgumentException(
          String.format("Amount transferred cannot be negative. amount: %s", amount.toString()));
    }
    ImmutableMap.Builder<ByteArray, BigDecimal> accountBalancesBuilder = ImmutableMap.builder();

    try {
      databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                // Get account balances.
                ImmutableMap<ByteArray, BigDecimal> accountBalances =
                    readAccountBalances(fromAccountId, toAccountId, transaction);

                BigDecimal newSourceAmount = accountBalances.get(fromAccountId).subtract(amount);
                BigDecimal newDestAmount = accountBalances.get(toAccountId).add(amount);

                if (newSourceAmount.signum() == -1) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Cannot transfer amount greater than original balance. fromAccount balance: %s, amount: %s",
                          accountBalances.get(fromAccountId).toString(), amount.toString()));
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
                return null;
              });
      return accountBalancesBuilder.build();
    } catch (SpannerException e) {
      // filter for IllegalArgumentExceptions thrown in lambda function above
      Throwable cause = e.getCause();
      if (cause instanceof IllegalArgumentException) {
        throw new IllegalArgumentException(cause.getMessage());
      }
      throw new SpannerDaoException(e);
    }
  }

  @Override
  public ImmutableList<TransactionEntry> getRecentTransactionsForAccount(
      ByteArray accountId, Timestamp beginTimestamp, Timestamp endTimestamp)
      throws SpannerDaoException {
    Statement statement =
        Statement.newBuilder(
                "SELECT * "
                    + "FROM TransactionHistory "
                    + "WHERE AccountId = @accountId AND "
                    + "EventTimestamp BETWEEN @beginTimestamp AND @endTimestamp "
                    + "ORDER BY EventTimestamp")
            .bind("accountId")
            .to(accountId)
            .bind("beginTimestamp")
            .to(beginTimestamp.toString())
            .bind("endTimestamp")
            .to(endTimestamp.toString())
            .build();
    try {
      ResultSet resultSet = databaseClient.singleUse().executeQuery(statement);
      return readTransactionHistories(resultSet);
    } catch (SpannerException e) {
      throw new SpannerDaoException(e);
    }
  }

  private ImmutableList<TransactionEntry> readTransactionHistories(ResultSet resultSet) {
    ImmutableList.Builder<TransactionEntry> transactionHistoriesBuilder = ImmutableList.builder();
    while (resultSet.next()) {
      transactionHistoriesBuilder.add(
          TransactionEntry.newBuilder()
              .setAccountId(ByteString.copyFrom(resultSet.getBytes("AccountId").toByteArray()))
              .setEventTimestamp(
                  com.google.finapp.Timestamp.newBuilder()
                      .setNanos(resultSet.getTimestamp("EventTimestamp").getNanos())
                      .setSeconds(resultSet.getTimestamp("EventTimestamp").getSeconds()))
              .setIsCredit(resultSet.getBoolean("IsCredit"))
              .setAmount(resultSet.getString("Amount"))
              .setDescription(resultSet.getString("Description"))
              .build());
    }
    return transactionHistoriesBuilder.build();
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
