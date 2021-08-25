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
import java.math.BigDecimal;

/** The SpannerDaoInterface defines the methods to be used by its separate implementations. */
public interface SpannerDaoInterface {

  /** Inserts a new row to the Customer table in the database. */
  void createCustomer(ByteArray customerId, String name, String address) throws SpannerDaoException;

  /**
   * Inserts a new row to the Account table in the database.
   *
   * @param accountType indicates unspecified, checking, or savings Account type
   * @param accountStatus indicates unspecified, active, or frozen Account status
   * @param balance non-negative account balance
   */
  void createAccount(
      ByteArray accountId, AccountType accountType, AccountStatus accountStatus, BigDecimal balance)
      throws SpannerDaoException;

  /** Inserts a new row to the CustomerRole table for a Customer in the database. */
  void createCustomerRole(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws SpannerDaoException;

  /**
   * Moves an amount from one unique account to another unique account for a Customer in the
   * database by modifying the unique accounts in the Account table and adding rows to the
   * TransactionHistory table in the database.
   *
   * @param fromAccountId unique account id where amount will be transferred from
   * @param toAccountId unique account id where amount will be transferred to
   * @param amount amount transferred from fromAccountId to toAccountId, must be less than or equal
   *     to fromAccountId's account balance, must be non-negative
   * @return mapping of both accounts' balances after the transfer was made, keyed by id
   */
  ImmutableMap<ByteArray, BigDecimal> moveAccountBalance(
      ByteArray fromAccountId, ByteArray toAccountId, BigDecimal amount) throws SpannerDaoException;

  /**
   * Modifies the account's balance in the Account table, subtracting the amount if isCredit and
   * adding the amount if not isCredit. Adds the transaction to the TransactionHistory table.
   *
   * @param amount amount added to or removed from the account, must be less than or equal to the
   *     account's balance if isCredit, must be non-negative
   * @param isCredit boolean for if the amount should be removed from the account
   * @return new balance of the account after the transaction
   */
  BigDecimal createTransactionForAccount(ByteArray accountId, BigDecimal amount, boolean isCredit)
      throws SpannerDaoException;

  /**
   * Returns list of TransactionEntry objects for the given AccountId in the given closed interval
   * of timestamps
   *
   * @param beginTimestamp timestamp for where query begins
   * @param endTimestamp timestamp for where query ends
   */
  ImmutableList<TransactionEntry> getRecentTransactionsForAccount(
      ByteArray accountId, Timestamp beginTimestamp, Timestamp endTimestamp)
      throws SpannerDaoException;
}
