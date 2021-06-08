package com.google.finapp;

import com.google.cloud.spanner.SpannerException;
import com.google.inject.Inject;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.math.BigDecimal;
import java.util.UUID;

final class FinAppService extends FinAppGrpc.FinAppImplBase {

  private final SpannerDao spannerDao;

  @Inject
  FinAppService(SpannerDao spannerDao) {
    this.spannerDao = spannerDao;
  }

  @Override
  public void createCustomer(Customer customer, StreamObserver<Empty> responseObserver) {
    try {
      spannerDao.createCustomer(
          UuidConverter.getBytesFromUuid(UUID.randomUUID()),
          customer.getName(),
          customer.getAddress());
    } catch (SpannerException e) {
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
    } catch (SpannerException e) {
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
