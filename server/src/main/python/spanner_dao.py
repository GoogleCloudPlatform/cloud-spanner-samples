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

import base64

from google.cloud.spanner_v1 import Client


class SpannerDao:
    def __init__(self, client: Client, instance_id, database_id) -> None:
        self._client = client
        self._database = self._client.instance(instance_id).database(
            database_id
        )

    def CreateCustomer(self, customer_id, name, address):
        with self._database.batch() as batch:
            batch.insert(
                table="Customer",
                columns=["CustomerId", "Name", "Address"],
                values=[[base64.b64encode(customer_id), name, address]],
            )
