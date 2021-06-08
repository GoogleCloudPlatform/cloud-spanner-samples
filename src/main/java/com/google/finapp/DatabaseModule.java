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
