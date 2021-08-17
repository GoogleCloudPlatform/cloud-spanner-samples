package com.google.finapp;

import com.google.cloud.ByteArray;
import com.google.finapp.FinAppGrpc.FinAppBlockingStub;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;

public class WorkloadClient {

  private final ManagedChannel channel;

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
    CreateAccountResponse response = blockingStub.createAccount(request);
    System.out.println(ByteArray.copyFrom(response.getAccountId().toByteArray()));
    return response.getAccountId();
  }

  public void moveAccountBalance(ByteString fromAccountId, ByteString toAccountId, String amount) {
    FinAppBlockingStub blockingStub = FinAppGrpc.newBlockingStub(channel);
    MoveAccountBalanceRequest request =
        MoveAccountBalanceRequest.newBuilder()
            .setAmount(amount)
            .setFromAccountId(fromAccountId)
            .setToAccountId(toAccountId)
            .build();
    MoveAccountBalanceResponse response = blockingStub.moveAccountBalance(request);
    System.out.println(
        response.getFromAccountIdBalance() + " and " + response.getToAccountIdBalance());
  }
}
