# Copyright 2021 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import uuid

import grpc
from google.api_core.exceptions import GoogleAPICallError
from google.cloud import spanner

import service_pb2
import service_pb2_grpc
from spanner_dao import SpannerDao


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
        customer_id = uuid.uuid4().bytes
        try:
            self._spanner_dao.CreateCustomer(
                customer_id, request.name, request.address
            )
        except GoogleAPICallError as e:
            context.abort(e.grpc_status_code, e.message)
        return service_pb2.CreateCustomerResponse(customer_id=customer_id)
