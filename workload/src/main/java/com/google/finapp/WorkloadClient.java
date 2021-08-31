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
import com.google.finapp.CreateAccountRequest.Status;
import com.google.finapp.FinAppGrpc.FinAppBlockingStub;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A gRPC client for the finance sample app. Performs a series of tasks (gRPC methods) in order. */
public class WorkloadClient implements Runnable {

  private final FinAppBlockingStub blockingStub;
  private static final Logger logger = Logger.getLogger(WorkloadClient.class.getName());
  private final ImmutableList<Task> tasks;
  private final List<ByteString> ids;
  private final Random random = new Random();

  private WorkloadClient(ManagedChannel channel, List<Task> tasks) {
    this.blockingStub = FinAppGrpc.newBlockingStub(channel);
    this.tasks = ImmutableList.copyOf(tasks);
    this.ids = new ArrayList<>();
  }

  /** @param tasks ImmutableList of tasks to complete in given order */
  public static WorkloadClient getWorkloadClient(ManagedChannel channel, List<Task> tasks) {
    return new WorkloadClient(channel, tasks);
  }

  @Override
  public void run() {
    for (Task task : tasks) {
      switch (task) {
        case CreateAccount:
          addAccountWithRandomBalance();
          break;
        case MoveAccountBalance:
          for (int i = 0; i < 2; i++) { // ensure that 2 accounts exist
            addAccountWithRandomBalance();
          }
          List<ByteString> randomIds = getRandomUniqueIds(2);
          moveAccountBalance(randomIds.get(0), randomIds.get(1), getRandomAmountFromRange(1, 200));
          break;
      }
    }
  }

  private static final int MAX_INITIAL_ACCOUNT_BALANCE = 20000;

  private void addAccountWithRandomBalance() {
    ids.add(
        createAccount(
            getRandomAmountFromRange(0, MAX_INITIAL_ACCOUNT_BALANCE),
            CreateAccountRequest.Type.CHECKING,
            Status.ACTIVE));
  }

  private BigDecimal getRandomAmountFromRange(int min, int max) {
    return BigDecimal.valueOf(random.nextInt(max - min) + min);
  }

  private List<ByteString> getRandomUniqueIds(int numIds) {
    if (numIds > ids.size()) {
      throw new IllegalArgumentException("Cannot get more ids than exist");
    }
    List<ByteString> idsCopy = new ArrayList<>(ids);
    List<ByteString> randomIds = new ArrayList<>();
    for (int i = 0; i < numIds; i++) {
      int index = random.nextInt(idsCopy.size());
      randomIds.add(idsCopy.get(index));
      idsCopy.remove(index);
    }
    return randomIds;
  }

  private ByteString createAccount(
      BigDecimal balance, CreateAccountRequest.Type type, CreateAccountRequest.Status status)
      throws StatusRuntimeException {
    CreateAccountRequest request =
        CreateAccountRequest.newBuilder()
            .setBalance(balance.toString())
            .setType(type)
            .setStatus(status)
            .build();
    try {
      CreateAccountResponse response = blockingStub.createAccount(request);
      logger.log(Level.INFO, String.format("Account created %s", response));
      return response.getAccountId();
    } catch (StatusRuntimeException e) {
      logger.log(Level.SEVERE, String.format("Error creating account %s", request));
      throw e;
    }
  }

  private void moveAccountBalance(
      ByteString fromAccountId, ByteString toAccountId, BigDecimal amount)
      throws StatusRuntimeException {
    MoveAccountBalanceRequest request =
        MoveAccountBalanceRequest.newBuilder()
            .setAmount(amount.toString())
            .setFromAccountId(fromAccountId)
            .setToAccountId(toAccountId)
            .build();
    try {
      MoveAccountBalanceResponse response = blockingStub.moveAccountBalance(request);
      logger.log(Level.INFO, String.format("Move made %s", response));
    } catch (StatusRuntimeException e) {
      logger.log(Level.SEVERE, String.format("Error making move %s", request));
      throw e;
    }
  }
}
