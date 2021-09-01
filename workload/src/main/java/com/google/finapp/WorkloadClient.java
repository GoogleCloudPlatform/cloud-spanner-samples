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

import com.google.finapp.FinAppGrpc.FinAppBlockingStub;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A gRPC client for the finance sample app. Runs until externally terminated and sends randomly
 * chosen requests using available methods.
 */
public class WorkloadClient implements Runnable {

  private static final Logger logger = Logger.getLogger(WorkloadClient.class.getName());
  private static final int MAX_INITIAL_ACCOUNT_BALANCE = 20000;
  private final List<ByteString> ids;
  private final Random random = new Random();
  private final FinAppBlockingStub blockingStub;

  private WorkloadClient(ManagedChannel channel) {
    this.blockingStub = FinAppGrpc.newBlockingStub(channel);
    this.ids = new ArrayList<>();
  }

  public static WorkloadClient getWorkloadClient(ManagedChannel channel) {
    return new WorkloadClient(channel);
  }

  @Override
  public void run() {
    for (int i = 0; i < 2; i++) { // ensure that > 2 accounts exists for future methods
      addAccountWithRandomBalance();
    }
    int numMethods = 2; // must be updated when new methods are added
    while (true) {
      int method = random.nextInt(numMethods);
      switch (method) {
        case 0:
          addAccountWithRandomBalance();
          break;
        case 1:
          int idsSize = ids.size();
          int fromAcctIndex = random.nextInt(idsSize);
          int toAcctIndex;
          do {
            toAcctIndex = random.nextInt(idsSize);
          } while (toAcctIndex == fromAcctIndex);
          moveAccountBalance(
              ids.get(fromAcctIndex), ids.get(toAcctIndex), getRandomAmountFromRange(1, 200));
          break;
      }
    }
  }

  private void addAccountWithRandomBalance() {
    ids.add(
        createAccount(
            getRandomAmountFromRange(0, MAX_INITIAL_ACCOUNT_BALANCE),
            CreateAccountRequest.Type.CHECKING,
            CreateAccountRequest.Status.ACTIVE));
  }

  private BigDecimal getRandomAmountFromRange(int min, int max) {
    return BigDecimal.valueOf(random.nextInt(max - min) + min);
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
      ByteString fromAccountId, ByteString toAccountId, BigDecimal amount) {
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
      if (e.getStatus().getCode().equals(Status.INVALID_ARGUMENT.getCode())) {
        logger.log(
            Level.INFO,
            String.format("Ignoring invalid argument error in moveAccountBalance: %s", e));
      } else {
        logger.log(Level.SEVERE, String.format("Unexpected error in moveAccountBalance: %s", e));
        throw e;
      }
    }
  }
}
