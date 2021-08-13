package com.google.finapp;

import com.google.finapp.CreateAccountRequest.Status;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class WorkloadGenerator {
  public static void main(String[] argv) {
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
    WorkloadClient client = new WorkloadClient(channel);
    System.out.println(
        client.createAccount(
            "324",
            CreateAccountRequest.Type.UNSPECIFIED_ACCOUNT_TYPE,
            Status.UNSPECIFIED_ACCOUNT_STATUS));
    System.out.println(
        new WorkloadClient(channel)
            .createAccount(
                "32455",
                CreateAccountRequest.Type.UNSPECIFIED_ACCOUNT_TYPE,
                Status.UNSPECIFIED_ACCOUNT_STATUS));
  }
}
