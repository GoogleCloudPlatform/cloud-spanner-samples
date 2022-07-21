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
import random
import requests

# Players generate items and money at 5:2 ratio. We don't want to devalue the currency!
class GameLoad(HttpUser):
    def on_start(self):
        self.getItems()

    def getItems(self):
        headers = {"Content-Type": "application/json"}
        r = requests.get(f"{self.host}/items", headers=headers)

        global itemUUIDs
        itemUUIDs = json.loads(r.text)

    def generateAmount(self):
        return str(round(random.uniform(1.01, 49.99), 2))

    @task(2)
    def acquireMoney(self):
        headers = {"Content-Type": "application/json"}

        # Get a random player that's part of a game, and update balance
        with self.client.get("/players", headers=headers, catch_response=True) as response:
            try:
                data = {"playerUUID": response.json()["playerUUID"], "amount": self.generateAmount(), "source": "loot"}
                self.client.put("/players/balance", data=json.dumps(data), headers=headers)
            except json.JSONDecodeError:
                response.failure("Response could not be decoded as JSON")
            except KeyError:
                response.failure("Response did not contain expected key 'playerUUID'")

    @task(5)
    def acquireItem(self):
        headers = {"Content-Type": "application/json"}

        # Get a random player that's part of a game, and add an item
        with self.client.get("/players", headers=headers, catch_response=True) as response:
            try:
                itemUUID = itemUUIDs[random.randint(0, len(itemUUIDs)-1)]
                data = {"playerUUID": response.json()["playerUUID"], "itemUUID": itemUUID, "source": "loot"}
                self.client.post("/players/items", data=json.dumps(data), headers=headers)
            except json.JSONDecodeError:
                response.failure("Response could not be decoded as JSON")
            except KeyError:
                response.failure("Response did not contain expected key 'playerUUID'")
