package com.google.finapp;

import com.google.cloud.spanner.SpannerException;
import com.google.inject.Inject;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

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
}
