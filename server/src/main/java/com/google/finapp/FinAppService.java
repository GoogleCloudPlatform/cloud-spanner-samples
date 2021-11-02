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

import com.google.cloud.ByteArray;
import com.google.cloud.Timestamp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.util.UUID;

final class FinAppService extends FinAppGrpc.FinAppImplBase {

  private final SpannerDaoInterface spannerDao;

  FinAppService(SpannerDaoInterface spannerDao) {
    this.spannerDao = spannerDao;
  }

  @Override
  public void ping(Empty empty, StreamObserver<PingResponse> responseObserver) {
    responseObserver.onNext(PingResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void createCustomer(
      CreateCustomerRequest customer, StreamObserver<CreateCustomerResponse> responseObserver) {
    ByteArray customerId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    try {
      spannerDao.createCustomer(customerId, customer.getName(), customer.getAddress());
    } catch (StatusException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    CreateCustomerResponse response =
        CreateCustomerResponse.newBuilder()
            .setCustomerId(ByteString.copyFrom(customerId.toByteArray()))
            .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void createAccount(
      CreateAccountRequest account, StreamObserver<CreateAccountResponse> responseObserver) {
    ByteArray accountId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    try {
      BigDecimal balance = getNonNegativeBigDecimal(account.getBalance());
      spannerDao.createAccount(accountId, toStorageAccountStatus(account.getStatus()), balance);
    } catch (StatusException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    CreateAccountResponse response =
        CreateAccountResponse.newBuilder()
            .setAccountId(ByteString.copyFrom(accountId.toByteArray()))
            .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void createCustomerRole(
      CreateCustomerRoleRequest role, StreamObserver<CreateCustomerRoleResponse> responseObserver) {
    ByteArray roleId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    try {
      spannerDao.createCustomerRole(
          ByteArray.copyFrom(role.getCustomerId().toByteArray()),
          ByteArray.copyFrom(role.getAccountId().toByteArray()),
          roleId,
          role.getName());
    } catch (StatusException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    CreateCustomerRoleResponse response =
        CreateCustomerRoleResponse.newBuilder()
            .setRoleId(ByteString.copyFrom(roleId.toByteArray()))
            .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void moveAccountBalance(
      MoveAccountBalanceRequest request,
      StreamObserver<MoveAccountBalanceResponse> responseObserver) {
    ImmutableMap<ByteArray, BigDecimal> accountBalances;
    ByteArray fromAccountId = ByteArray.copyFrom(request.getFromAccountId().toByteArray());
    ByteArray toAccountId = ByteArray.copyFrom(request.getToAccountId().toByteArray());
    try {
      BigDecimal amount = getNonNegativeBigDecimal(request.getAmount());
      accountBalances = spannerDao.moveAccountBalance(fromAccountId, toAccountId, amount);
    } catch (StatusException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    MoveAccountBalanceResponse response =
        MoveAccountBalanceResponse.newBuilder()
            .setFromAccountIdBalance(accountBalances.get(fromAccountId).toString())
            .setToAccountIdBalance(accountBalances.get(toAccountId).toString())
            .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void createTransactionForAccount(
      CreateTransactionForAccountRequest request,
      StreamObserver<CreateTransactionForAccountResponse> responseObserver) {
    BigDecimal newBalance;
    try {
      BigDecimal amount = getNonNegativeBigDecimal(request.getAmount());
      newBalance =
          spannerDao.createTransactionForAccount(
              ByteArray.copyFrom(request.getAccountId().toByteArray()),
              amount,
              request.getIsCredit());
    } catch (StatusException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    responseObserver.onNext(
        CreateTransactionForAccountResponse.newBuilder()
            .setNewBalance(newBalance.toString())
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void getRecentTransactionsForAccount(
      GetRecentTransactionsForAccountRequest request,
      StreamObserver<GetRecentTransactionsForAccountResponse> responseObserver) {
    ImmutableList<TransactionEntry> transactionEntries;
    ByteArray accountId = ByteArray.copyFrom(request.getAccountId().toByteArray());
    Timestamp beginTimestamp = Timestamp.fromProto(request.getBeginTimestamp());
    Timestamp endTimestamp = Timestamp.fromProto(request.getEndTimestamp());
    if (endTimestamp.equals(
        Timestamp.fromProto(com.google.protobuf.Timestamp.getDefaultInstance()))) {
      // If endTimestamp is not set, default to no upper bound.
      endTimestamp = Timestamp.MAX_VALUE;
    }
    try {
      if (beginTimestamp.compareTo(endTimestamp) > 0) {
        throw Status.INVALID_ARGUMENT
            .withDescription(
                String.format(
                    "Invalid timestamp range. %s is after %s.", beginTimestamp, endTimestamp))
            .asException();
      }
      transactionEntries =
          spannerDao.getRecentTransactionsForAccount(
              accountId, beginTimestamp, endTimestamp, request.getMaxEntryCount());
    } catch (StatusException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    GetRecentTransactionsForAccountResponse response =
        GetRecentTransactionsForAccountResponse.newBuilder()
            .addAllTransactionEntry(transactionEntries)
            .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  private static AccountStatus toStorageAccountStatus(
      CreateAccountRequest.Status apiAccountStatus) {
    switch (apiAccountStatus) {
      case ACTIVE:
        return AccountStatus.ACTIVE;
      case FROZEN:
        return AccountStatus.FROZEN;
      default:
        return AccountStatus.UNSPECIFIED_ACCOUNT_STATUS;
    }
  }

  private BigDecimal getNonNegativeBigDecimal(String value) throws StatusException {
    BigDecimal valueDecimal;
    try {
      valueDecimal = new BigDecimal(value);
    } catch (NumberFormatException e) {
      throw Status.INVALID_ARGUMENT
          .withDescription(String.format("Invalid numeric value: %s", value))
          .asException();
    }
    if (valueDecimal.signum() == -1) {
      throw Status.INVALID_ARGUMENT
          .withDescription(
              String.format("Expected positive numeric value, found: %s instead", value))
          .asException();
    }
    return valueDecimal;
  }
}
