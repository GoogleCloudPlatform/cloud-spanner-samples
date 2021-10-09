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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An executable method for a workload generator for the finance sample app that creates traffic by
 * sending gRPC requests to the server.
 */
public final class WorkloadMain {
  private static final Logger logger = Logger.getLogger(WorkloadMain.class.getName());

  private static class WorkloadGenerator {
    private final ManagedChannel channel;

    WorkloadGenerator(ManagedChannel channel) {
      this.channel = channel;
    }

    void startSteadyLoad(int threadCount) {
      ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
      for (int i = 0; i < threadCount; i++) {
        executor.submit(WorkloadClient.getWorkloadClient(channel));
        logger.log(Level.INFO, "WorkloadClient created");
      }
    }
  }

  /**
   * Generates gRPC clients to run indefinitely in separate threads and generate traffic for the
   * finance app server.
   */
  public static void main(String[] argv) throws Exception {
    Args args = new Args();
    JCommander.newBuilder().addObject(args).build().parse(argv);
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(args.address, args.port).usePlaintext().build();
    WorkloadGenerator workloadGenerator = new WorkloadGenerator(channel);
    workloadGenerator.startSteadyLoad(args.threadCount);
  }

  @Parameters(separators = "=")
  private static class Args {
    @Parameter(
        names = {"--address-name", "-a"},
        description = "Address of the finapp server.")
    String address = "localhost";

    @Parameter(
        names = {"--port", "-p"},
        description = "GRPC port of finapp server.")
    int port = 8080;

    @Parameter(
        names = {"--thread-count", "-t"},
        description = "Number of threads to use, to control parallelism.")
    int threadCount = 10;
  }
}
