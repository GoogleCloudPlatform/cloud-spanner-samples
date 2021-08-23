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
import io.grpc.StatusRuntimeException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkloadClient {

  private final FinAppBlockingStub blockingStub;
  private static final Logger logger = Logger.getLogger(WorkloadClient.class.getName());

  private WorkloadClient(ManagedChannel channel) {
    this.blockingStub = FinAppGrpc.newBlockingStub(channel);
  }

  public static WorkloadClient getWorkloadClient(ManagedChannel channel) {
    return new WorkloadClient(channel);
  }

  public ByteString createAccount(
      String balance, CreateAccountRequest.Type type, CreateAccountRequest.Status status)
      throws StatusRuntimeException {
    CreateAccountRequest request =
        CreateAccountRequest.newBuilder()
            .setBalance(balance)
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

  public void moveAccountBalance(ByteString fromAccountId, ByteString toAccountId, String amount)
      throws StatusRuntimeException {
    MoveAccountBalanceRequest request =
        MoveAccountBalanceRequest.newBuilder()
            .setAmount(amount)
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
