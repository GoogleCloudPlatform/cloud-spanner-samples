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

import com.google.cloud.spanner.SpannerException;
import com.google.inject.Inject;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

final class FinAppService extends FinAppGrpc.FinAppImplBase {

  private final SpannerDaoInterface spannerDao;

  @Inject
  FinAppService(SpannerDaoInterface spannerDao) {
    this.spannerDao = spannerDao;
  }

  @Override
  public void createCustomer(Customer customer, StreamObserver<Empty> responseObserver) {
    try {
      spannerDao.createCustomer(
          UuidConverter.getBytesFromUuid(UUID.randomUUID()),
          customer.getName(),
          customer.getAddress());
    } catch (SQLException e) {
      responseObserver.onError(Status.fromThrowable(e).asException());
      return;
    }
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void createAccount(Account account, StreamObserver<Empty> responseObserver) {
    try {
      spannerDao.createAccount(
          UuidConverter.getBytesFromUuid(UUID.randomUUID()),
          toStorageAccountType(account.getType()),
          toStorageAccountStatus(account.getStatus()),
          new BigDecimal(account.getBalance()));
    } catch (SQLException e) {
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
    responseObserver.onNext(Empty.getDefaultInstance());
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
