package com.google.finapp;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
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

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface Port {}

  private static class Args {
    @Parameter(names = {"--port", "-p"})
    int port = 8080;
  }
}
