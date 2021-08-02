package com.google.finapp;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=")
class Args {

  @Parameter(names = {"--port", "-p"})
  int port = 8080;

  @Parameter(names = {"--spanner_host"})
  String spannerHost = "spanner.googleapis.com";

  @Parameter(names = {"--spanner_port"})
  int spannerPort = 443;

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
