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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

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
  public static void main(String[] args) {
    CommandLine cmd = parseArgs(args);
    String addressName = cmd.getOptionValue("a");
    int port;
    int threadCount;
    try {
      port = ((Number) cmd.getParsedOptionValue("p")).intValue();
      threadCount = ((Number) cmd.getParsedOptionValue("t")).intValue();
    } catch (ParseException e) {
      throw new IllegalArgumentException("Input value cannot be parsed.", e);
    }
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(addressName, port).usePlaintext().build();
    WorkloadGenerator workloadGenerator = new WorkloadGenerator(channel);
    workloadGenerator.startSteadyLoad(threadCount);
  }

  private static CommandLine parseArgs(String[] args) {
    Options options = new Options();

    options.addOption(
        Option.builder("a")
            .longOpt("address-name")
            .desc("server address name")
            .required(true)
            .type(String.class)
            .hasArg()
            .build());

    options.addOption(
        Option.builder("p")
            .longOpt("port")
            .desc("server port")
            .required(true)
            .type(Number.class)
            .hasArg()
            .build());

    options.addOption(
        Option.builder("t")
            .longOpt("thread-count")
            .desc("number of threads to use in thread pool")
            .required(true)
            .type(Number.class)
            .hasArg()
            .build());

    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();

    try {
      return parser.parse(options, args);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp(
          String.format(
              "java -jar %s",
              Paths.get("WorkloadMain.java").toAbsolutePath().normalize().toString()),
          options);
      System.exit(1);
      return null;
    }
  }
}
