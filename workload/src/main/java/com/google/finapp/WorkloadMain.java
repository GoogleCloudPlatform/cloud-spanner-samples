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

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorkloadMain {
  private static final String DEFAULT_ACCOUNT_BALANCE = "10000";
  private static final String DEFAULT_TRANSFER_AMOUNT = "20";

  private static class WorkloadGenerator {
    private final ManagedChannel channel;
    private final List<ByteString> ids = new ArrayList<>();

    WorkloadGenerator(ManagedChannel channel) {
      this.channel = channel;
    }

    void seedData() {
      for (int i = 0; i < 200; i++) {
        ByteString response =
            WorkloadClient.getWorkloadClient(channel)
                .createAccount(
                    DEFAULT_ACCOUNT_BALANCE,
                    CreateAccountRequest.Type.UNSPECIFIED_ACCOUNT_TYPE,
                    CreateAccountRequest.Status.UNSPECIFIED_ACCOUNT_STATUS);
        if (response != null) {
          ids.add(response);
        }
      }
    }

    void startSteadyLoad() {
      Random random = new Random();
      int numIds = ids.size();
      if (numIds == 0) {
        throw new IllegalStateException("No accounts were created successfully.");
      }
      while (true) {
        ByteString fromId = ids.get(random.nextInt(numIds));
        ByteString toId = ids.get(random.nextInt(numIds));
        if (fromId.equals(toId)) {
          continue;
        }
        WorkloadClient.getWorkloadClient(channel)
            .moveAccountBalance(fromId, toId, DEFAULT_TRANSFER_AMOUNT);
      }
    }
  }

  public static void main(String[] args) {
    String addressName = args[0];
    int port = Integer.parseInt(args[1]);
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(addressName, port).usePlaintext().build();
    WorkloadGenerator workloadGenerator = new WorkloadGenerator(channel);
    workloadGenerator.seedData();
    workloadGenerator.startSteadyLoad();
  }
}
