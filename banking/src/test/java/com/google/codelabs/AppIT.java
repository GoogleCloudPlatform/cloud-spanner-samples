/*
 * Copyright 2025 Google LLC
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

package com.google.codelabs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Random;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AppIT {

  // The Spanner instance needs to exist for tests to pass
  private static String instanceId = System.getenv("SPANNER_TEST_INSTANCE");
  private static String databaseId = formatForTest(System.getenv("SPANNER_TEST_DATABASE"));

  private static String formatForTest(String name) {
    if (name == null) {
      name = "";
      String characters = "abcdefghijklmnopqrstuvwxyz";
      Random random = new Random();
      for (int i = 0; i < 5; i++) {
        char c = characters.charAt(random.nextInt(26));
        name += c;
      }
    }
    return name + "-" + UUID.randomUUID().toString().substring(0, 12);
  }

  private static DatabaseId db;
  private static DatabaseAdminClient dbAdminClient;

  private String runSample(String command, String... commandOptions) throws Exception {
    PrintStream stdOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bout);
    System.setOut(out);

    String[] args = new String[1 + commandOptions.length];
    args[0] = command;
    for (int i = 0; i < commandOptions.length; i++) {
      args[i + 1] = commandOptions[i];
    }

    App.main(args);
    System.setOut(stdOut);
    return bout.toString();
  }

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty("SPANNER_INSTANCE", instanceId);
    System.setProperty("SPANNER_DATABASE", databaseId);

    SpannerOptions options = SpannerOptions.newBuilder().build();
    Spanner spanner = options.getService();

    db = DatabaseId.of(options.getProjectId(), instanceId, databaseId);
    dbAdminClient = spanner.getDatabaseAdminClient();

    dbAdminClient.dropDatabase(db.getInstanceId().getInstance(), db.getDatabase());
  }

  @AfterClass
  public static void tearDown() throws Exception {
    dbAdminClient.dropDatabase(db.getInstanceId().getInstance(), db.getDatabase());
  }

  @Test
  public void testSample() throws Exception {
    assertNotNull(instanceId);
    assertNotNull(databaseId);

    String out = runSample("create");
    assertTrue(out.contains("Created Spanner database"));
    assertTrue(out.contains(db.getName()));

    out = runSample("insert", "customers");
    assertTrue(out.contains("Inserted") && out.contains("customers"));

    out = runSample("insert", "accounts");
    assertTrue(out.contains("Inserted") && out.contains("accounts"));

    out = runSample("insert", "transactions");
    assertTrue(out.contains("Inserted") && out.contains("transactions"));

    out = runSample("categorize");
    assertTrue(out.contains("Completed categorizing transactions"));

    out = runSample("query", "email", "madi");
    assertTrue(out.contains("Customer emails matching"));

    out = runSample("query", "balance", "1");
    assertTrue(out.contains("Account balances for customer"));

    out = runSample("query", "spending", "1", "groceries");
    assertTrue(out.contains("Total spending for customer"));

    // No tests for BigQuery-related functionality
  }

}
