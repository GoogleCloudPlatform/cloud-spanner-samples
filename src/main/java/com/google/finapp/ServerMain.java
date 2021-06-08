package com.google.finapp;

import com.google.inject.Guice;
import com.google.inject.Injector;

public final class ServerMain {
  private ServerMain() {}

  public static void main(String[] argv) throws Exception {
    Injector injector = Guice.createInjector(new DatabaseModule(), new ArgsModule(argv));
    FinAppServer server = injector.getInstance(FinAppServer.class);
    server.start();
    server.blockUntilShutdown();
  }
}
