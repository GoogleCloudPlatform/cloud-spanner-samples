/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.finapp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class FinAppIT {

  private static SpannerDaoInterface JDBCDao;
  private static SpannerDaoInterface JavaDao;
  private static DatabaseClient databaseClient;

  static {
    // set all IDs according to emulator setup
    String spannerProjectId = "test-project";
    String spannerInstanceId = "test-instance";
    String spannerDatabaseId = "test-database";
    JDBCDao = new SpannerDaoJDBCImpl(spannerProjectId, spannerInstanceId, spannerDatabaseId);
    SpannerOptions spannerOptions = SpannerOptions.getDefaultInstance();
    Spanner spanner = spannerOptions.toBuilder().build().getService();
    databaseClient = spanner
        .getDatabaseClient(DatabaseId.of(spannerProjectId, spannerInstanceId, spannerDatabaseId));
    JavaDao = new SpannerDaoImpl(databaseClient);
  }

  private SpannerDaoInterface getImpl(String impl) {
    if (impl.equals("java client")) {
      return JavaDao;
    } else {
      return JDBCDao;
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"java client", "jdbc"})
  public void createAccountTest(String impl) throws SpannerDaoException {
    SpannerDaoInterface spannerDao = getImpl(impl);
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    spannerDao.createAccount(
        accountId,
        AccountType.UNSPECIFIED_ACCOUNT_TYPE /* = 0*/,
        AccountStatus.UNSPECIFIED_ACCOUNT_STATUS /* = 0*/,
        new BigDecimal(2));
    try (ResultSet resultSet =
        databaseClient
            .singleUse()
            .read(
                "Account",
                KeySet.newBuilder().addKey(Key.of(accountId)).build(),
                Arrays.asList("AccountType", "AccountStatus", "Balance"))) {
      while (resultSet.next()) {
        assertEquals(0, resultSet.getLong(0));
        assertEquals(0, resultSet.getLong(1));
        assertEquals(new BigDecimal(2), resultSet.getBigDecimal(2));
      }
    }
  }
}
