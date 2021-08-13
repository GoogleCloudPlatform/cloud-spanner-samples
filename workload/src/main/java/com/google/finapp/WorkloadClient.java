package com.google.finapp;

import com.google.finapp.FinAppGrpc.FinAppBlockingStub;
import com.google.protobuf.ByteString;
import io.grpc.*;

public class WorkloadClient {

  private final ManagedChannel channel;

  public WorkloadClient(ManagedChannel channel) {
    this.channel = channel;
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
    return response.getAccountId();
  }
}
