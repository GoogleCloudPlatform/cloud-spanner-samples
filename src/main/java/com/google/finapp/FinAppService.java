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
  public void createCustomer(Customer customer, StreamObserver<CustomerResponse> responseObserver) {
    ByteArray customerId;
    try {
      customerId = spannerDao.createCustomer(
          UuidConverter.getBytesFromUuid(UUID.randomUUID()),
          customer.getName(),
          customer.getAddress());
    } catch (SpannerDaoException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    CustomerResponse response = CustomerResponse.newBuilder()
        .setId(ByteString.copyFrom(customerId.toByteArray())).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void createAccount(Account account, StreamObserver<AccountResponse> responseObserver) {
    ByteArray accountId;
    try {
      accountId = spannerDao.createAccount(
          UuidConverter.getBytesFromUuid(UUID.randomUUID()),
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
    AccountResponse response = AccountResponse.newBuilder()
        .setId(ByteString.copyFrom(accountId.toByteArray())).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void addAccountForCustomer(CustomerRole role,
      StreamObserver<CustomerRoleResponse> responseObserver) {
    ByteArray roleId;
    try {
      roleId = spannerDao.addAccountForCustomer(
          ByteArray.copyFrom(role.getCustomerId().toByteArray()),
          ByteArray.copyFrom(role.getAccountId().toByteArray()),
          UuidConverter.getBytesFromUuid(UUID.randomUUID()),
          role.getName());
    } catch (SpannerDaoException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    CustomerRoleResponse response = CustomerRoleResponse.newBuilder()
        .setId(ByteString.copyFrom(roleId.toByteArray())).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  private static AccountType toStorageAccountType(Account.Type apiAccountType) {
    switch (apiAccountType) {
      case CHECKING:
        return AccountType.CHECKING;
      case SAVING:
        return AccountType.SAVING;
      default:
        return AccountType.UNSPECIFIED_ACCOUNT_TYPE;
    }
  }

  private static AccountStatus toStorageAccountStatus(Account.Status apiAccountStatus) {
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
