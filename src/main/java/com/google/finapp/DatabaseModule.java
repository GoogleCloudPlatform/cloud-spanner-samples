package com.google.finapp;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

final class DatabaseModule extends AbstractModule {

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  DatabaseClient provideDatabaseClient() {
    SpannerOptions spannerOptions = SpannerOptions.getDefaultInstance();
    Spanner spanner = spannerOptions.getService();
    return spanner.getDatabaseClient(
        DatabaseId.of(spannerOptions.getProjectId(), "test-instance", "test-database"));
  }
}
