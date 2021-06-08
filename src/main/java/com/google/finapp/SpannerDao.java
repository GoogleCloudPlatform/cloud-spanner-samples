/*
 * Copyright 2021 Google LLC
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>https://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.finapp;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.Value;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import java.math.BigDecimal;

final class SpannerDao {

  private final DatabaseClient databaseClient;

  @Inject
  SpannerDao(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  void createCustomer(ByteArray customerId, String name, String address) throws SpannerException {
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
  }

  void createAccount(
      ByteArray accountId, AccountType accountType, AccountStatus accountStatus, BigDecimal balance)
      throws SpannerException {
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
  }
}
