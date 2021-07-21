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
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

final class ArgsModule extends AbstractModule {

  private final Args args;

  ArgsModule(String[] argv) {
    this.args = new Args();
    JCommander.newBuilder().addObject(args).build().parse(argv);
  }

  @Override
  protected void configure() {}

  @Provides
  @Port
  int providePort() {
    return args.port;
  }

  @Provides
  @SpannerHost
  String provideSpannerHost() {
    return args.spannerHost;
  }

  @Provides
  @SpannerPort
  int provideSpannerPort() {
    return args.spannerPort;
  }

  @Provides
  @SpannerProjectId
  String provideSpannerProjectId() {
    return args.spannerProjectId;
  }

  @Provides
  @SpannerInstanceId
  String provideSpannerInstanceId() {
    return args.spannerInstanceId;
  }

  @Provides
  @SpannerDatabaseId
  String provideSpannerDatabaseId() {
    return args.spannerDatabaseId;
  }

  @Provides
  @SpannerUseJdbc
  String provideSpannerUseJdbc() { return args.spannerUseJdbc; }

  @Parameters(separators = "=")
  private static class Args {
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

    @Parameter(names = {"--spanner_use_jdbc"}, arity = 1)
    boolean spannerUseJdbc = false;
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface Port {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SpannerHost {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SpannerPort {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SpannerProjectId {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SpannerInstanceId {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SpannerDatabaseId {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SpannerUseJdbc {}
}
