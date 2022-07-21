# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from locust import HttpUser, task

import json

# Players can sell and buy items
class TradeLoad(HttpUser):
    def itemMarkup(self, value):
        f = float(value)
        return str(f*1.5)

    @task
    def sellItem(self):
        headers = {"Content-Type": "application/json"}

        # Get a random item
        with self.client.get("/trades/player_items", headers=headers, catch_response=True) as response:
            try:
                playerUUID = response.json()["PlayerUUID"]
                playerItemUUID = response.json()["PlayerItemUUID"]
                list_price = self.itemMarkup(response.json()["Price"])

                data = {"lister": playerUUID, "playerItemUUID": playerItemUUID, "list_price": list_price}
                self.client.post("/trades/sell", data=json.dumps(data), headers=headers)
            except json.JSONDecodeError:
                response.failure("Response could not be decoded as JSON")
            except KeyError:
                response.failure("Response did not contain expected key 'playerUUID'")

    @task
    def buyItem(self):
        headers = {"Content-Type": "application/json"}

        # Get a random item
        with self.client.get("/trades/open", headers=headers, catch_response=True) as response:
            try:
                orderUUID = response.json()["OrderUUID"]
                buyerUUID = response.json()["BuyerUUID"]

                data = {"orderUUID": orderUUID, "buyer": buyerUUID}
                self.client.put("/trades/buy", data=json.dumps(data), headers=headers)
            except json.JSONDecodeError:
                response.failure("Response could not be decoded as JSON")
            except KeyError:
                response.failure("Response did not contain expected key 'playerUUID'")


