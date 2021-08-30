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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
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
 * A workload generator for the finance sample app that creates traffic by sending gRPC requests to
 * the server.
 */
public final class WorkloadMain {
  private static final Logger logger = Logger.getLogger(WorkloadMain.class.getName());

  private static class WorkloadGenerator {
    private final ManagedChannel channel;
    private static final int DEFAULT_MAX_QUEUE_SIZE = 100;
    private final Random random = new Random();
    private final List<Task> taskValues =
        Collections.unmodifiableList(Arrays.asList(Task.values()));
    private final int numTasks = taskValues.size();

    WorkloadGenerator(ManagedChannel channel) {
      this.channel = channel;
    }

    void startSteadyLoad(int threadCount, int taskCount) {
      ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
      BlockingQueue<Runnable> queue = executor.getQueue();
      while (true) {
        if (queue.size() < DEFAULT_MAX_QUEUE_SIZE) {
          ImmutableList<Task> tasks = generateRandomTasks(taskCount);
          logger.log(Level.INFO, String.format("Tasks submitted %s", tasks.toString()));
          executor.submit(WorkloadClient.getWorkloadClient(channel, tasks));
        }
      }
    }

    ImmutableList<Task> generateRandomTasks(int taskCount) {
      ImmutableList.Builder<Task> taskListBuilder = ImmutableList.builder();
      for (int i = 0; i < taskCount; i++) {
        taskListBuilder.add(taskValues.get(random.nextInt(numTasks)));
      }
      return taskListBuilder.build();
    }
  }

  /**
   * Continuously generates gRPC clients with given number of threads and tasks per thread, as defined by
   * commandline arguments.
   */
  public static void main(String[] args) {
    CommandLine cmd = parseArgs(args);
    String addressName = cmd.getOptionValue("a");
    int port;
    int threadCount;
    int taskCount;
    try {
      port = ((Number) cmd.getParsedOptionValue("p")).intValue();
      threadCount = ((Number) cmd.getParsedOptionValue("t")).intValue();
      taskCount = ((Number) cmd.getParsedOptionValue("c")).intValue();
    } catch (ParseException e) {
      throw new IllegalArgumentException("Input value cannot be parsed.", e);
    }
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(addressName, port).usePlaintext().build();
    WorkloadGenerator workloadGenerator = new WorkloadGenerator(channel);
    workloadGenerator.startSteadyLoad(threadCount, taskCount);
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

    options.addOption(
        Option.builder("c")
            .longOpt("task-count")
            .desc("number of tasks (RPC methods) each thread performs")
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
