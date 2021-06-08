package com.google.finapp;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.SpannerException;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

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
}
