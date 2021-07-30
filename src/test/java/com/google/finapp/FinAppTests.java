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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FinAppTests {

  private static SpannerDaoInterface JDBCDao;
  private static SpannerDaoInterface JavaDao;
  private static DatabaseClient databaseClient;

  @BeforeClass
  public static void setUpTests() {
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

  @Test
  public void createAccountTest() throws SpannerDaoException {
    ByteArray accountIdJDBC = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    ByteArray accountIdJava = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    JDBCDao.createAccount(
        accountIdJDBC,
        AccountType.UNSPECIFIED_ACCOUNT_TYPE,
        AccountStatus.UNSPECIFIED_ACCOUNT_STATUS,
        new BigDecimal(2));
    JavaDao.createAccount(
        accountIdJava,
        AccountType.UNSPECIFIED_ACCOUNT_TYPE,
        AccountStatus.UNSPECIFIED_ACCOUNT_STATUS,
        new BigDecimal(36));
    try (ResultSet resultSet =
        databaseClient
            .singleUse()
            .read(
                "Account",
                KeySet.newBuilder().addKey(Key.of(accountIdJDBC)).addKey(Key.of(accountIdJava))
                    .build(),
                Arrays.asList("AccountType", "AccountStatus", "Balance", "AccountId"))) {
      boolean JDBCSeen = false;
      boolean JavaSeen = false;
      while (resultSet.next()) {
        Assert.assertEquals(0, resultSet.getLong(0));
        Assert.assertEquals(0, resultSet.getLong(1));
        if (resultSet.getBytes(3).equals(accountIdJDBC)) {
          Assert.assertEquals(new BigDecimal(2), resultSet.getBigDecimal(2));
          JDBCSeen = true;
        } else if (resultSet.getBytes(3).equals(accountIdJava)) {
          Assert.assertEquals(new BigDecimal(36), resultSet.getBigDecimal(2));
          JavaSeen = true;
        }
      }
      assert JDBCSeen;
      assert JavaSeen;
    }
  }
}
