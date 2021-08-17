package com.google.finapp;

import com.google.finapp.CreateAccountRequest.Status;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorkloadGenerator {
  public static void main(String[] argv) {
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
    List<ByteString> ids = new ArrayList();
    for (int i = 0; i < 200; i++) {
      ids.add(
          WorkloadClient.getWorkloadClient(channel)
              .createAccount(
                  "1000",
                  CreateAccountRequest.Type.UNSPECIFIED_ACCOUNT_TYPE,
                  Status.UNSPECIFIED_ACCOUNT_STATUS));
    }
    Random random = new Random();
    int numIds = ids.size();
    for (int i = 0; i < 1000; i++) {
      ByteString fromId = ids.get(random.nextInt(numIds));
      ByteString toId = ids.get(random.nextInt(numIds));
      if (fromId.equals(toId)) {
        continue;
      }
      WorkloadClient.getWorkloadClient(channel).moveAccountBalance(fromId, toId, "20");
    }
  }
}
