#!/usr/bin/env python
# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


from models.stations import *
from models.people import *
from models.utils import *
from models.rides import *
from google.cloud import spanner
from sys import exit

if __name__ == "__main__":
    s = spanner.Client()
    instance = s.instance("transit")
    client = instance.database("transitdb")
    stations = Stations()
    client.run_in_transaction(writeSpanner, stations)
    addresses = Addresses()
    client.run_in_transaction(writeSpanner, addresses)
    people = Persons()
    client.run_in_transaction(writeSpanner, people)

    inhabitants = HasInhabitants()
    client.run_in_transaction(writeSpanner, inhabitants)

    oysters = Oysters()
    client.run_in_transaction(writeSpanner, oysters)

    hasoysters = HasOysters()
    client.run_in_transaction(writeSpanner, hasoysters)

    routes = Routes()
    client.run_in_transaction(writeSpanner, routes)
    shortest_routes = ShortestRoutes()
    for i in shortest_routes.split(1000):
        x = ShortestRoutes(fromCsv=False)
        x.list_items = i
        client.run_in_transaction(writeSpanner, x)

    rides = Rides()
    for i in rides.split(1000):
        x = Rides(fromCsv=False)
        x.list_items = i
        client.run_in_transaction(writeSpanner, x)
