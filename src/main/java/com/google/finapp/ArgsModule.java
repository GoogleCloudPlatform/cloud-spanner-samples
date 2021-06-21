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
  @SpannerProjectId
  String provideSpannerProjectId() {
    return args.spannerProjectId;
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface Port {}

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SpannerProjectId {}

  @Parameters(separators = "=")
  private static class Args {
    @Parameter(names = {"--port", "-p"})
    int port = 8080;

    @Parameter(names = {"--spanner_project_id"})
    String spannerProjectId;
  }
}
