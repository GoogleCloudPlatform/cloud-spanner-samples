package com.google.finapp;

import com.google.finapp.FinAppGrpc.FinAppBlockingStub;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkloadClient {

  private final ManagedChannel channel;
  private static Logger logger = Logger.getLogger(WorkloadClient.class.getName());

  private WorkloadClient(ManagedChannel channel) {
    this.channel = channel;
  }

  public static WorkloadClient getWorkloadClient(ManagedChannel channel) {
    return new WorkloadClient(channel);
  }

  public ByteString createAccount(
      String balance, CreateAccountRequest.Type type, CreateAccountRequest.Status status) {
    FinAppBlockingStub blockingStub = FinAppGrpc.newBlockingStub(channel);
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
      return null;
    }
  }

  public void moveAccountBalance(ByteString fromAccountId, ByteString toAccountId, String amount) {
    FinAppBlockingStub blockingStub = FinAppGrpc.newBlockingStub(channel);
    MoveAccountBalanceRequest request =
        MoveAccountBalanceRequest.newBuilder()
            .setAmount(amount)
            .setFromAccountId(fromAccountId)
            .setToAccountId(toAccountId)
            .build();
    try {
      MoveAccountBalanceResponse response = blockingStub.moveAccountBalance(request);
      logger.log(Level.INFO, String.format("Move made %s", response));
      return;
    } catch (StatusRuntimeException e) {
      logger.log(Level.SEVERE, String.format("Error making move %s", request));
      return;
    }
  }
}
