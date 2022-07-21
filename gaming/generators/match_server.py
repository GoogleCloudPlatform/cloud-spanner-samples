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

# Generate games
# A game consists of 100 players. Only 1 winner randomly selected from those players
#
# Matchmaking is random list of players that are not playing
#
# To achieve this
# A locust user 'GameMatch' will start off by creating a "game"
# Then, pre-selecting a subset of users, and set a current_game attribute for those players.
# Once done, after a period of time, a winner is randomly selected.


# TODO: Matchmaking should ideally be handled by Agones. Once done, Locust test would convert to testing Agones match-making
# Create and close game matches
class GameMatch(HttpUser):

    @task(2)
    def createGame(self):
        headers = {"Content-Type": "application/json"}

        # Create the game
        # TODO: Make number of players configurable
        res = self.client.post("/games/create", headers=headers)

        # TODO: Store the response into memory to be used to close the game later, to avoid a call to the DB

    @task(1)
    def closeGame(self):
        # Get a game that's currently open, then close it
        headers = {"Content-Type": "application/json"}
        with self.client.get("/games/open", headers=headers, catch_response=True) as response:
            try:
                data = {"gameUUID": response.json()["gameUUID"]}
                self.client.put("/games/close", data=json.dumps(data), headers=headers)
            except json.JSONDecodeError:
                response.failure("Response could not be decoded as JSON")
            except KeyError:
                response.failure("Response did not contain expected key 'playerUUID'")


