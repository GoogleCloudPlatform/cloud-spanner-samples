package com.google.finapp;

import com.google.finapp.CreateAccountRequest.Status;

public class WorkloadGenerator {
  public static void main(String[] argv) {
    WorkloadClient client = new WorkloadClient("localhost", 8080);
    System.out.println(
        client.createAccount(
            "324",
            CreateAccountRequest.Type.UNSPECIFIED_ACCOUNT_TYPE,
            Status.UNSPECIFIED_ACCOUNT_STATUS));
  }
}
