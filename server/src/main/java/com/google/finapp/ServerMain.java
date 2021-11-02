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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;

public final class ServerMain {
  private ServerMain() {}

  public static void main(String[] argv) throws Exception {
    Args args = new Args();
    JCommander.newBuilder().addObject(args).build().parse(argv);

    SpannerDaoInterface spannerDao =
        getSpannerDao(
            args.spannerUseJdbc,
            args.spannerProjectId,
            args.spannerInstanceId,
            args.spannerDatabaseId);
    FinAppServer server = new FinAppServer(args.port, new FinAppService(spannerDao));
    server.start();
    server.blockUntilShutdown();
  }

  private static SpannerDaoInterface getSpannerDao(
      boolean spannerUseJdbc,
      String spannerProjectId,
      String spannerInstanceId,
      String spannerDatabaseId) {
    if (spannerUseJdbc) {
      return new SpannerDaoJDBCImpl(spannerProjectId, spannerInstanceId, spannerDatabaseId);
    }

    SpannerOptions spannerOptions = SpannerOptions.getDefaultInstance();
    Spanner spanner = spannerOptions.toBuilder().build().getService();
    DatabaseClient client =
        spanner.getDatabaseClient(
            DatabaseId.of(spannerProjectId, spannerInstanceId, spannerDatabaseId));
    return new SpannerDaoImpl(client);
  }

  @Parameters(separators = "=")
  private static class Args {
    @Parameter(names = {"--port", "-p"})
    int port = 8080;

    @Parameter(names = {"--spanner_project_id"})
    String spannerProjectId;

    @Parameter(names = {"--spanner_instance_id"})
    String spannerInstanceId;

    @Parameter(names = {"--spanner_database_id"})
    String spannerDatabaseId;

    @Parameter(
        names = {"--spanner_use_jdbc"},
        arity = 0)
    boolean spannerUseJdbc = false;
  }
}
