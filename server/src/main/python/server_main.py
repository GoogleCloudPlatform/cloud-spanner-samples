"""The Python implementation of the GRPC helloworld.Greeter server."""

import logging
import uuid
from concurrent import futures

import grpc
from google.cloud import spanner
from grpc_reflection.v1alpha import reflection

import service_pb2
import service_pb2_grpc
from spanner_dao import SpannerDao


class FinAppService(service_pb2_grpc.FinAppServicer):
    def __init__(self) -> None:
        super().__init__()
        self._spanner_dao = SpannerDao(
            spanner.Client(), "test-instance", "test-database"
        )

    def CreateCustomer(
        self,
        request: service_pb2.CreateAccountRequest,
        context: grpc.ServicerContext,
    ):
        customer_id = uuid.uuid1().bytes
        self._spanner_dao.CreateCustomer(
            customer_id, request.name, request.address
        )
        return service_pb2.CreateCustomerResponse(customer_id=customer_id)


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    service_pb2_grpc.add_FinAppServicer_to_server(FinAppService(), server)
    SERVICE_NAMES = (
        service_pb2.DESCRIPTOR.services_by_name["FinApp"].full_name,
        reflection.SERVICE_NAME,
    )
    reflection.enable_server_reflection(SERVICE_NAMES, server)
    server.add_insecure_port("[::]:8080")
    server.start()
    print("Started server on 8080")
    server.wait_for_termination()


if __name__ == "__main__":
    logging.basicConfig()
    serve()