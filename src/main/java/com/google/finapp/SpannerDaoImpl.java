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
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
  public void moveAccountBalance(ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount)
      throws SpannerDaoException {
    if (amount.signum() == -1) {
      throw new IllegalArgumentException(
          String.format("Amount transferred cannot be negative. amount: %s", amount.toString()));
    }
    try {
      databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                // Get account balances.
                ImmutableMap<ByteArray, BigDecimal> accountBalances =
                    readAccountBalances(fromAccountId, toAccountId, transaction);

                BigDecimal newSourceAmount = accountBalances.get(fromAccountId).subtract(amount);

                if (newSourceAmount.signum() == -1) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Account balance cannot be negative. original account balance: %s, amount transferred: %s",
                          accountBalances.get(fromAccountId).toString(), amount.toString()));
                }

                transaction.buffer(
                    ImmutableList.of(
                        buildUpdateAccountMutation(fromAccountId, newSourceAmount),
                        buildUpdateAccountMutation(
                            toAccountId, accountBalances.get(toAccountId).add(amount)),
                        buildInsertTransactionHistoryMutation(
                            fromAccountId, amount, /* isCredit= */ true),
                        buildInsertTransactionHistoryMutation(
                            toAccountId, amount, /* isCredit= */ false)));
                return null;
              });
    } catch (SpannerException e) {
      throw new SpannerDaoException(e);
    }
  }

  @Override
  public BigDecimal createTransactionForAccount(
      ByteArray accountId, BigDecimal amount, boolean isCredit) throws SpannerDaoException {
    if (amount.signum() == -1) {
      throw new IllegalArgumentException(
          String.format("Amount transferred cannot be negative. amount: %s", amount.toString()));
    }
    try {
      List<BigDecimal> newBalanceList = new ArrayList();
      databaseClient
          .readWriteTransaction()
          .run(
              transaction -> {
                // Get account balances.
                ResultSet resultSet =
                    transaction.read(
                        "Account",
                        KeySet.singleKey(Key.of(accountId)),
                        ImmutableList.of("Balance"));
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
                          "Account balance cannot be negative. original account balance: %s, amount transferred: %s",
                          oldBalance.toString(), amount.toString()));
                }
                transaction.buffer(
                    ImmutableList.of(
                        buildUpdateAccountMutation(accountId, newBalance),
                        buildInsertTransactionHistoryMutation(accountId, amount, isCredit)));
                newBalanceList.add(newBalance);
                return null;
              });
      return newBalanceList.get(0);
    } catch (SpannerException e) {
      // filter for IllegalArgumentExceptions thrown in lambda function above
      Throwable cause = e.getCause();
      if (cause instanceof IllegalArgumentException) {
        throw new IllegalArgumentException(cause.getMessage());
      }
      throw new SpannerDaoException(e);
    }
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
