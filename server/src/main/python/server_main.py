# Copyright 2021 Google LLC

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#     https://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""The Python implementation of the GRPC helloworld.Greeter server."""

import argparse
import logging
import uuid
from concurrent import futures

import grpc
from google.cloud import spanner
from grpc_reflection.v1alpha import reflection

import service_pb2
import service_pb2_grpc
from spanner_dao import SpannerDao

_SERVER_THREAD_POOL_SIZE = 10


class FinAppService(service_pb2_grpc.FinAppServicer):
    def __init__(
        self, project_id: str, instance_id: str, database_id: str
    ) -> None:
        super().__init__()
        self._spanner_dao = SpannerDao(
            spanner.Client(project=project_id), instance_id, database_id
        )

    def CreateCustomer(
        self,
        request: service_pb2.CreateCustomerRequest,
        context: grpc.ServicerContext,
    ) -> service_pb2.CreateCustomerResponse:
        customer_id = uuid.uuid1().bytes
        self._spanner_dao.CreateCustomer(
            customer_id, request.name, request.address
        )
        return service_pb2.CreateCustomerResponse(customer_id=customer_id)


def _Serve(args):
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=_SERVER_THREAD_POOL_SIZE)
    )
    service_pb2_grpc.add_FinAppServicer_to_server(
        FinAppService(
            args.spanner_project_id,
            args.spanner_instance_id,
            args.spanner_Database_id,
        ),
        server,
    )
    SERVICE_NAMES = (
        service_pb2.DESCRIPTOR.services_by_name["FinApp"].full_name,
        reflection.SERVICE_NAME,
    )
    reflection.enable_server_reflection(SERVICE_NAMES, server)
    server.add_insecure_port(f"[::]:{args.port}")
    server.start()
    logging.info(f"Started server on {args.port}")
    server.wait_for_termination()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument(
        "--spanner_project_id", type=str, default="test-project"
    )
    parser.add_argument(
        "--spanner_instance_id", type=str, default="test-instance"
    )
    parser.add_argument(
        "--spanner_database_id", type=str, default="test-database"
    )
    _Serve(parser.parse_args())
