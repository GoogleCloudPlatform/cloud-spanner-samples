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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * A workload generator for the finance sample app that creates traffic by sending gRPC requests to
 * the server.
 */
public final class WorkloadMain {
  private static final String DEFAULT_ACCOUNT_BALANCE = "10000";
  private static final Logger logger = Logger.getLogger(WorkloadMain.class.getName());

  private static class WorkloadGenerator {
    private final ManagedChannel channel;
    private final List<ByteString> ids = new ArrayList<>();
    private final Random random = new Random();

    WorkloadGenerator(ManagedChannel channel) {
      this.channel = channel;
    }

    void startSteadyLoad() {
      for (int i = 0; i < 32; i++) {
        WorkloadClient.getWorkloadClient(
                channel,
                ImmutableList.of(Task.MoveAccountBalance, Task.CreateAccount, Task.CreateAccount))
            .start(String.valueOf(i));
      }
    }
  }

  public static void main(String[] args) {
    CommandLine cmd = parseArgs(args);
    String addressName = cmd.getOptionValue("a");
    int port;
    try {
      port = ((Number) cmd.getParsedOptionValue("p")).intValue();
    } catch (ParseException e) {
      throw new IllegalArgumentException("Input value cannot be parsed.", e);
    }
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(addressName, port).usePlaintext().build();
    WorkloadGenerator workloadGenerator = new WorkloadGenerator(channel);
    workloadGenerator.startSteadyLoad();
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
