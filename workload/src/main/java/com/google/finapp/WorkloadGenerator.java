package com.google.finapp;

import com.google.finapp.CreateAccountRequest.Status;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorkloadGenerator {
  public static void main(String[] args) {
    String addressName = args[0];
    int port = Integer.parseInt(args[1]);
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(addressName, port).usePlaintext().build();
    List<ByteString> ids = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      ByteString response =
          WorkloadClient.getWorkloadClient(channel)
              .createAccount(
                  "1000",
                  CreateAccountRequest.Type.UNSPECIFIED_ACCOUNT_TYPE,
                  Status.UNSPECIFIED_ACCOUNT_STATUS);
      if (response != null) {
        ids.add(response);
      }
    }
    Random random = new Random();
    int numIds = ids.size();
    if (numIds == 0) {
      throw new RuntimeException("No accounts were created successfully.");
    }
    while (true) {
      ByteString fromId = ids.get(random.nextInt(numIds));
      ByteString toId = ids.get(random.nextInt(numIds));
      if (fromId.equals(toId)) {
        continue;
      }
      WorkloadClient.getWorkloadClient(channel).moveAccountBalance(fromId, toId, "20");
    }
  }
}
