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
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.util.UUID;

final class FinAppService extends FinAppGrpc.FinAppImplBase {

  private final SpannerDaoInterface spannerDao;

  @Inject
  FinAppService(SpannerDaoInterface spannerDao) {
    this.spannerDao = spannerDao;
  }

  @Override
  public void createCustomer(
      CreateCustomerRequest customer, StreamObserver<CreateCustomerResponse> responseObserver) {
    ByteArray customerId = UuidConverter.getBytesFromUuid(UUID.randomUUID());
    try {
      spannerDao.createCustomer(customerId, customer.getName(), customer.getAddress());
    } catch (SpannerDaoException e) {
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
      spannerDao.createAccount(
          accountId,
          toStorageAccountType(account.getType()),
          toStorageAccountStatus(account.getStatus()),
          new BigDecimal(account.getBalance()));
    } catch (SpannerDaoException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    } catch (NumberFormatException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT
              .withCause(e)
              .withDescription(
                  String.format(
                      "Invalid balance - %s. Expected a NUMERIC value", account.getBalance()))
              .asException());
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
    } catch (SpannerDaoException e) {
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
      MoveAccountBalanceRequest request, StreamObserver<Empty> responseObserver) {
    try {
      spannerDao.moveAccountBalance(
          ByteArray.copyFrom(request.getFromAccountId().toByteArray()),
          ByteArray.copyFrom(request.getToAccountId().toByteArray()),
          new BigDecimal(request.getAmount()));
    } catch (SpannerDaoException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  private static AccountType toStorageAccountType(CreateAccountRequest.Type apiAccountType) {
    switch (apiAccountType) {
      case CHECKING:
        return AccountType.CHECKING;
      case SAVING:
        return AccountType.SAVING;
      default:
        return AccountType.UNSPECIFIED_ACCOUNT_TYPE;
    }
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
}
