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

import string
import json
import random
import requests

# Generate player load with 5:1 reads to write
class PlayerLoad(HttpUser):
    def on_start(self):
        self.getValidUUIDs()

    def getValidUUIDs(self):
        headers = {"Content-Type": "application/json"}
        r = requests.get(f"{self.host}/players", headers=headers)

        global pUUIDs
        pUUIDs = json.loads(r.text)

    def generatePlayerName(self):
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=32))

    def generatePassword(self):
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=32))

    def generateEmail(self):
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=32) + ['@'] +
            random.choices(['gmail', 'yahoo', 'microsoft']) + ['.com'])

    @task
    def createPlayer(self):
        headers = {"Content-Type": "application/json"}
        data = {"player_name": self.generatePlayerName(), "email": self.generateEmail(), "password": self.generatePassword()}

        self.client.post("/players", data=json.dumps(data), headers=headers)

    @task(5)
    def getPlayer(self):
        pUUID = pUUIDs[random.randint(0, len(pUUIDs)-1)]
        headers = {"Content-Type": "application/json"}

        self.client.get(f"/players/{pUUID}", headers=headers, name="/players/[playerUUID]")
