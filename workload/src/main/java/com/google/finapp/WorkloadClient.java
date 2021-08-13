package com.google.finapp;

import com.google.finapp.FinAppGrpc.FinAppBlockingStub;
import com.google.finapp.FinAppGrpc.FinAppStub;
import com.google.protobuf.ByteString;
import io.grpc.*;

public class WorkloadClient {

  private final FinAppBlockingStub blockingStub;
  private final FinAppStub asyncStub;

  public WorkloadClient(ManagedChannel channel) {
    blockingStub = FinAppGrpc.newBlockingStub(channel);
    asyncStub = FinAppGrpc.newStub(channel);
  }

  public ByteString createAccount(
      String balance, CreateAccountRequest.Type type, CreateAccountRequest.Status status) {
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
