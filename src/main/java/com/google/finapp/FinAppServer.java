package com.google.finapp;

import com.google.inject.Inject;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

final class FinAppServer {

  private static final Logger logger = Logger.getLogger(FinAppServer.class.getName());

  private final int port;
  private final Server grpcServer;

  @Inject
  FinAppServer(@ArgsModule.Port int port, FinAppService finAppService) {
    this.port = port;
    this.grpcServer =
        ServerBuilder.forPort(port)
            .addService(finAppService)
            .addService(ProtoReflectionService.newInstance())
            .build();
  }

  void start() throws IOException {
    grpcServer.start();
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                  System.err.println("*** shutting down gRPC server since JVM is shutting down");
                  try {
                    FinAppServer.this.stop();
                  } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                  }
                  System.err.println("*** server shut down");
                }));
  }

  void stop() throws InterruptedException {
    if (grpcServer != null) {
      grpcServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  void blockUntilShutdown() throws InterruptedException {
    if (grpcServer != null) {
      grpcServer.awaitTermination();
    }
  }
}
