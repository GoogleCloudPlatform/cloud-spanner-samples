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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.IntegrationTest;
import com.google.cloud.spanner.IntegrationTestEnv;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.testing.RemoteSpannerHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(IntegrationTest.class)
public class FinAppIT {

  private static SpannerDaoInterface daoJDBC;
  private static SpannerDaoInterface daoJava;
  private static DatabaseClient databaseClient;
  private static Database db;

  @ClassRule
  public static IntegrationTestEnv env = new IntegrationTestEnv();

  @BeforeClass
  public static void setup() throws IOException {
    RemoteSpannerHelper testHelper = env.getTestHelper();
    db = testHelper
        .createTestDatabase(extractStatementsFromSDLFile("src/main/java/com/google/finapp/schema.sdl"));
    daoJDBC = new SpannerDaoJDBCImpl(testHelper.getOptions().getProjectId(),
        testHelper.getInstanceId().getInstance(), db.getId().getDatabase());
    databaseClient = testHelper.getDatabaseClient(db);
    daoJava = new SpannerDaoImpl(databaseClient);
  }

  @AfterClass
  public static void tearDown() {
    db.drop();
  }

  @Test
  public void createAccountTest() throws SpannerDaoException {
    for (SpannerDaoInterface spannerDao : List.of(daoJava, daoJDBC)) {
      ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
      BigDecimal bigDecimalTwo = new BigDecimal(2);
      spannerDao.createAccount(
          accountId,
          AccountType.UNSPECIFIED_ACCOUNT_TYPE /* = 0*/,
          AccountStatus.UNSPECIFIED_ACCOUNT_STATUS /* = 0*/,
          bigDecimalTwo);
      try (ResultSet resultSet =
          databaseClient
              .singleUse()
              .read(
                  "Account",
                  KeySet.newBuilder().addKey(Key.of(accountId)).build(),
                  Arrays.asList("AccountType", "AccountStatus", "Balance"))) {
        int count = 0;
        while (resultSet.next()) {
          assertThat(resultSet.getLong(0)).isEqualTo(0);
          assertThat(resultSet.getLong(1)).isEqualTo(0);
          assertThat(resultSet.getBigDecimal(2)).isEqualTo(bigDecimalTwo);
          count++;
        }
        assertThat(count).isEqualTo(1);
      }
    }
  }


  static String[] extractStatementsFromSDLFile(String filename) throws FileNotFoundException {
    File file = new File(filename);
    BufferedReader reader = new BufferedReader(new FileReader(file));
    StringBuilder builder = new StringBuilder();
    try (Scanner scanner = new Scanner(reader)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        if (!line.startsWith("--")) { // ignore comments
          builder.append(line);
        }
      }
    }
    return builder.toString().split(";"); // separate into individual statements
  }
}
